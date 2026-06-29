package com.conceptualware.auth;

import com.conceptualware.domain.user.User;
import com.conceptualware.infrastructure.security.JwtService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #21 — Segurança: testes de fluxo de autenticação, JWT, lockout
 * Concept #19 — TDD: cobertura de invariantes de domínio
 */
@DisplayName("Fluxo de Autenticação — Testes Unitários")
class AuthFlowTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        // Segredo de 64 caracteres (>= 256 bits para HMAC-SHA-256)
        String secret = "A".repeat(64);
        jwtService = new JwtService(secret, 900_000L, 604_800_000L);
    }

    // ── JwtService ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JwtService — geração e validação de tokens")
    class JwtServiceTests {

        @Test
        @DisplayName("Access token gerado contém userId, email e roles")
        void accessTokenContainsClaims() {
            String token = jwtService.generateAccessToken("user-1", "test@example.com", Set.of("USER"));

            Claims claims = jwtService.parseToken(token);

            assertThat(claims.getSubject()).isEqualTo("user-1");
            assertThat(claims.get("email", String.class)).isEqualTo("test@example.com");
            assertThat(claims.get("type", String.class)).isEqualTo("access");
        }

        @Test
        @DisplayName("Refresh token contém userId e jti (para revogação)")
        void refreshTokenContainsJti() {
            String token = jwtService.generateRefreshToken("user-1");

            Claims claims = jwtService.parseToken(token);

            assertThat(claims.getSubject()).isEqualTo("user-1");
            assertThat(claims.get("type", String.class)).isEqualTo("refresh");
            assertThat(claims.get("jti", String.class)).isNotBlank();
        }

        @Test
        @DisplayName("isAccessToken retorna true apenas para access tokens")
        void tokenTypeDiscrimination() {
            String access  = jwtService.generateAccessToken("u1", "a@b.com", Set.of("USER"));
            String refresh = jwtService.generateRefreshToken("u1");

            assertThat(jwtService.isAccessToken(access)).isTrue();
            assertThat(jwtService.isAccessToken(refresh)).isFalse();
            assertThat(jwtService.isRefreshToken(refresh)).isTrue();
            assertThat(jwtService.isRefreshToken(access)).isFalse();
        }

        @Test
        @DisplayName("Token expirado é rejeitado por validateToken")
        void expiredTokenRejected() {
            JwtService shortLived = new JwtService("B".repeat(64), 1L, 1L); // 1 ms de expiração
            String token = shortLived.generateAccessToken("u1", "a@b.com", Set.of());

            // Aguarda expiração (1ms é suficiente, mas colocamos 10ms para garantia de clock)
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}

            assertThat(shortLived.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("Token com assinatura adulterada é rejeitado")
        void tamperedTokenRejected() {
            String token = jwtService.generateAccessToken("u1", "a@b.com", Set.of("USER"));
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";

            assertThat(jwtService.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("Token gerado com segredo diferente é rejeitado")
        void wrongSecretRejected() {
            JwtService otherService = new JwtService("C".repeat(64), 900_000L, 604_800_000L);
            String token = otherService.generateAccessToken("u1", "a@b.com", Set.of());

            assertThat(jwtService.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("extractRoles retorna os papéis corretos")
        void rolesExtracted() {
            String token = jwtService.generateAccessToken("u1", "a@b.com", Set.of("USER", "PREMIUM"));
            Set<String> roles = jwtService.extractRoles(token);

            assertThat(roles).containsExactlyInAnyOrder("USER", "PREMIUM");
        }

        @Test
        @DisplayName("getExpiration retorna instant no futuro para token válido")
        void expirationInFuture() {
            String token = jwtService.generateAccessToken("u1", "a@b.com", Set.of());
            Instant exp = jwtService.getExpiration(token);

            assertThat(exp).isAfter(Instant.now());
        }
    }

    // ── Domínio User — Value Objects ─────────────────────────────────────────

    @Nested
    @DisplayName("User.Email — Value Object")
    class EmailValueObjectTests {

        @Test
        @DisplayName("E-mail válido é aceito")
        void validEmail() {
            assertThatNoException().isThrownBy(() -> new User.Email("user@example.com"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "notanemail", "@nodomain", "no@", "", "  " })
        @DisplayName("E-mail inválido lança IllegalArgumentException")
        void invalidEmailRejected(String email) {
            assertThatThrownBy(() -> new User.Email(email))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("E-mail nulo lança NullPointerException")
        void nullEmailRejected() {
            assertThatThrownBy(() -> new User.Email(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("User.Username — Value Object")
    class UsernameValueObjectTests {

        @Test
        @DisplayName("Username válido é aceito")
        void validUsername() {
            assertThatNoException().isThrownBy(() -> new User.Username("alice_123"));
        }

        @ParameterizedTest
        @ValueSource(strings = { "ab", "a b", "user@name", "u".repeat(51) })
        @DisplayName("Username inválido lança IllegalArgumentException")
        void invalidUsernameRejected(String username) {
            assertThatThrownBy(() -> new User.Username(username))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Domínio User — Lockout ────────────────────────────────────────────────

    @Nested
    @DisplayName("User — Proteção contra força bruta")
    class BruteForceTests {

        private User user;

        @BeforeEach
        void setUp() {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // custo baixo para testes
            user = User.create("alice@example.com", "alice", encoder.encode("Password1"));
            user.verifyEmail(); // ativa a conta
        }

        @Test
        @DisplayName("Conta não está bloqueada inicialmente")
        void notLockedInitially() {
            assertThat(user.isLockedOut()).isFalse();
        }

        @Test
        @DisplayName("4 tentativas inválidas não bloqueiam a conta")
        void fourFailedAttemptsNoLockout() {
            for (int i = 0; i < 4; i++) user.recordFailedLogin();

            assertThat(user.isLockedOut()).isFalse();
        }

        @Test
        @DisplayName("5 tentativas inválidas bloqueiam a conta por 15 min")
        void fiveFailedAttemptsLockout() {
            for (int i = 0; i < 5; i++) user.recordFailedLogin();

            assertThat(user.isLockedOut()).isTrue();
            assertThat(user.lockoutRemaining().toMinutes()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("Login bem-sucedido reseta o contador de tentativas")
        void successfulLoginResetCounter() {
            for (int i = 0; i < 3; i++) user.recordFailedLogin();
            user.resetFailedLogins();

            assertThat(user.isLockedOut()).isFalse();
            // Deve aceitar mais 5 tentativas antes de bloquear novamente
            for (int i = 0; i < 4; i++) user.recordFailedLogin();
            assertThat(user.isLockedOut()).isFalse();
        }
    }

    // ── Domínio User — Gestão de Refresh Tokens ──────────────────────────────

    @Nested
    @DisplayName("User — Gerenciamento de sessões (refresh tokens)")
    class RefreshTokenTests {

        private User user;

        @BeforeEach
        void setUp() {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
            user = User.create("bob@example.com", "bob", encoder.encode("Password1"));
            user.verifyEmail();
        }

        @Test
        @DisplayName("Token armazenado é válido antes da expiração")
        void storedTokenIsValid() {
            String token = jwtService.generateRefreshToken("bob-id");
            user.storeRefreshToken(token);

            assertThat(user.isRefreshTokenValid(token)).isTrue();
        }

        @Test
        @DisplayName("Token revogado não é mais válido")
        void revokedTokenInvalid() {
            String token = jwtService.generateRefreshToken("bob-id");
            user.storeRefreshToken(token);
            user.revokeRefreshToken(token);

            assertThat(user.isRefreshTokenValid(token)).isFalse();
        }

        @Test
        @DisplayName("Limite de 5 sessões simultâneas é aplicado")
        void maxConcurrentSessions() {
            for (int i = 0; i < 6; i++) {
                user.storeRefreshToken("token-" + i);
            }
            // token-0 deve ter sido removido (mais antigo)
            assertThat(user.isRefreshTokenValid("token-0")).isFalse();
        }

        @Test
        @DisplayName("Coleções expostas são imutáveis")
        void collectionsMutabilityGuard() {
            assertThatThrownBy(() -> user.getRoles().add(User.Role.ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> user.getCompletedConcepts().add("concept-1"))
                .isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> user.getFavorites().add("algo-1"))
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ── Domínio User — Invariantes de Negócio ─────────────────────────────────

    @Nested
    @DisplayName("User — Invariantes de negócio")
    class BusinessRuleTests {

        private User user;

        @BeforeEach
        void setUp() {
            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
            user = User.create("carol@example.com", "carol", encoder.encode("Password1"));
        }

        @Test
        @DisplayName("Usuário criado começa em PENDING_VERIFICATION")
        void initialStatusPendingVerification() {
            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("Após verifyEmail, conta fica ACTIVE")
        void verifyEmailActivatesAccount() {
            user.verifyEmail();

            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("verifyEmail em conta já verificada lança exceção")
        void doubleVerificationThrows() {
            user.verifyEmail();

            assertThatThrownBy(user::verifyEmail)
                .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("addPoints atualiza nível de habilidade corretamente")
        void addPointsUpdatesSkillLevel() {
            user.verifyEmail();
            user.addPoints(500);

            assertThat(user.getSkillLevel()).isEqualTo(User.SkillLevel.INTERMEDIATE);
        }

        @Test
        @DisplayName("addPoints negativo lança IllegalArgumentException")
        void addNegativePointsThrows() {
            assertThatThrownBy(() -> user.addPoints(-10))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("progressPercentage retorna 0 para totalConcepts <= 0")
        void progressPercentageWithZeroTotal() {
            assertThat(user.progressPercentage(0)).isEqualTo(0.0);
            assertThat(user.progressPercentage(-1)).isEqualTo(0.0);
        }

        @Test
        @DisplayName("Evento UserRegisteredEvent é publicado na criação")
        void domainEventPublishedOnCreate() {
            var events = user.pullDomainEvents();

            assertThat(events).hasSize(1);
            assertThat(events.get(0).getEventType()).isEqualTo("user.registered");
        }

        @Test
        @DisplayName("pullDomainEvents limpa a lista de eventos")
        void pullEventsClearsQueue() {
            user.pullDomainEvents();

            assertThat(user.pullDomainEvents()).isEmpty();
        }
    }
}
