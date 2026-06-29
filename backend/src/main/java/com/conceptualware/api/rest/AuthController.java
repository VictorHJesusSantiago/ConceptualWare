package com.conceptualware.api.rest;

import com.conceptualware.domain.user.User;
import com.conceptualware.infrastructure.persistence.UserRepository;
import com.conceptualware.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * Concept #21 — Segurança: Autenticação, JWT, Refresh Token, BCrypt,
 *   Política de senhas, Gestão de sessões
 * Concept #25 — REST: Resource, POST, Request Body, Status Codes 201/401/409
 * Concept #14 — Clean Code: Guard clauses, early return
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        // OWASP A01 — user enumeration: mensagem idêntica para email e username existentes.
        // Mensagens distintas permitem que atacante confirme se um e-mail/username está cadastrado.
        boolean emailExists    = userRepository.existsByEmail(request.email());
        boolean usernameExists = userRepository.existsByUsername(request.username());
        if (emailExists || usernameExists)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Registration failed — credentials already in use");

        // BCrypt password hashing — Concept #21
        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.email(), request.username(), passwordHash);
        userRepository.save(user);

        log.info("User registered: username={}", request.username());
        return generateTokens(user);
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            // Mesma mensagem para "usuário não existe" e "senha errada" — evita enumeração.
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // Lockout check — Concept #21 (brute force protection)
        if (user.isLockedOut()) {
            long secondsRemaining = user.lockoutRemaining().getSeconds();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Account temporarily locked. Try again in " + secondsRemaining + " seconds.");
        }

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account not active");
        }

        // Constant-time comparison (prevent timing attacks) — Concept #21
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepository.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.resetFailedLogins();
        user.recordActivity();
        userRepository.save(user);

        log.info("User logged in: id={}", user.getId());
        return generateTokens(user);
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !jwtService.validateToken(refreshToken)
                || !jwtService.isRefreshToken(refreshToken))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");

        String userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!user.isRefreshTokenValid(refreshToken))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token revoked");

        user.revokeRefreshToken(refreshToken);
        userRepository.save(user);
        return generateTokens(user);
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) return ResponseEntity.badRequest().build();

        // Server-side session invalidation: actually revoke the refresh token so it
        // cannot be reused after logout. A07 — Authentication Failures.
        if (jwtService.validateToken(refreshToken) && jwtService.isRefreshToken(refreshToken)) {
            String userId = jwtService.extractUserId(refreshToken);
            userRepository.findById(userId).ifPresent(user -> {
                user.revokeRefreshToken(refreshToken);
                userRepository.save(user);
            });
        }
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse generateTokens(User user) {
        Set<String> roles = new java.util.HashSet<>();
        user.getRoles().forEach(r -> roles.add(r.name()));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        user.storeRefreshToken(refreshToken);
        userRepository.save(user);

        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername());
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String username,
        // Política sincronizada com o gateway (gateway/src/routes/auth.ts — RegisterSchema).
        // SSOT: qualquer mudança aqui deve refletir no schema Zod do gateway e vice-versa.
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter")
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain at least one lowercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one digit")
        String password
    ) {}

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    public record AuthResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String username
    ) {}
}
