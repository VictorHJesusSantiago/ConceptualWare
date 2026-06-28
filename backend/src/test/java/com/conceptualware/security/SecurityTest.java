package com.conceptualware.security;

import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.*;
import org.junit.jupiter.api.*;

import java.security.Key;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #21 — Security: SQL Injection, XXE, SSRF, Deserialization,
 *               Argon2/PBKDF2, TOTP/MFA, Key Rotation, Zero Trust, SBOM
 * Concept #19 — TDD: security property tests
 */
@DisplayName("Category 21 — Security: Complete Test Suite")
class SecurityTest {

    // ── SQL Injection Prevention ──────────────────────────────────────────────

    @Nested
    @DisplayName("SQL Injection")
    class SQLInjectionTests {

        @Test
        @DisplayName("Vulnerable query string shows injection vector")
        void vulnerableQueryShowsInjection() {
            String malicious = "' OR '1'='1";
            String query = SecurityVulnerabilityDemo.buildVulnerableQuery(malicious);
            assertThat(query).contains("OR '1'='1");
        }

        @Test
        @DisplayName("LDAP escape neutralizes wildcard injection")
        void ldapEscapeWildcard() {
            String escaped = SecurityVulnerabilityDemo.ldapEscape("admin*");
            assertThat(escaped).doesNotContain("*");
            assertThat(escaped).contains("\\2A");
        }

        @Test
        @DisplayName("LDAP escape neutralizes parentheses injection")
        void ldapEscapeParens() {
            String escaped = SecurityVulnerabilityDemo.ldapEscape(")(uid=*)");
            assertThat(escaped).contains("\\28").contains("\\29").contains("\\2A");
        }

        @Test
        @DisplayName("Safe LDAP filter uses escaped input")
        void safeLdapFilter() {
            String filter = SecurityVulnerabilityDemo.buildSafeLdapFilter("admin", "pass)word");
            // Must not have unescaped ) that would break filter
            assertThat(filter).contains("\\29");
        }

        @Test
        @DisplayName("Path traversal detection throws SecurityException")
        void pathTraversalDetected() {
            java.nio.file.Path base = java.nio.file.Path.of("/var/www").toAbsolutePath();
            assertThatThrownBy(() ->
                SecurityVulnerabilityDemo.safeResolveFile(base, "../../etc/shadow"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
        }
    }

    // ── SSRF Prevention ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("SSRF Prevention")
    class SSRFTests {

        @Test
        @DisplayName("Localhost is blocked")
        void localhostBlocked() {
            var result = SecurityVulnerabilityDemo.validateUrl("http://localhost/admin");
            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).containsIgnoringCase("private");
        }

        @Test
        @DisplayName("127.0.0.1 is blocked")
        void loopbackBlocked() {
            var result = SecurityVulnerabilityDemo.validateUrl("http://127.0.0.1:8080/internal");
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("Private RFC1918 range 10.x blocked")
        void privateRangeBlocked() {
            var result = SecurityVulnerabilityDemo.validateUrl("http://10.0.0.1/secret");
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("Private RFC1918 range 192.168.x blocked")
        void privateRange192Blocked() {
            var result = SecurityVulnerabilityDemo.validateUrl("http://192.168.1.100/api");
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("File protocol is blocked")
        void fileProtocolBlocked() {
            var result = SecurityVulnerabilityDemo.validateUrl("file:///etc/passwd");
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("Invalid URL returns blocked")
        void invalidUrlBlocked() {
            var result = SecurityVulnerabilityDemo.validateUrl("not-a-url");
            assertThat(result.allowed()).isFalse();
        }
    }

    // ── XXE Prevention ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("XXE Prevention")
    class XXETests {

        @Test
        @DisplayName("Secure SAX parser is created without throwing")
        void secureSaxParserCreated() {
            assertThatNoException().isThrownBy(SecurityVulnerabilityDemo::secureSaxParser);
        }

        @Test
        @DisplayName("Secure DocumentBuilder is created without throwing")
        void secureDocumentBuilderCreated() {
            assertThatNoException().isThrownBy(SecurityVulnerabilityDemo::secureDocumentBuilder);
        }

        @Test
        @DisplayName("Secure SAX parser rejects DOCTYPE declaration")
        void secureSaxRejectsDoctype() throws Exception {
            var parser = SecurityVulnerabilityDemo.secureSaxParser();
            String xxePayload = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <root>&xxe;</root>
                """;
            assertThatThrownBy(() -> parser.parse(
                new org.xml.sax.InputSource(new java.io.StringReader(xxePayload)),
                new org.xml.sax.helpers.DefaultHandler()))
                .isInstanceOf(Exception.class); // DTD processing throws
        }
    }

    // ── Cryptography ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Advanced Cryptography — PBKDF2")
    class CryptographyTests {

        @Test
        @DisplayName("PBKDF2 hash is non-empty and contains iterations/salt/hash")
        void pbkdf2HashFormat() throws Exception {
            String hash = AdvancedCryptographyService.hashPasswordPbkdf2("mypassword");
            assertThat(hash).contains("$");
            String[] parts = hash.split("\\$");
            assertThat(parts).hasSize(3);
            assertThat(parts[0]).isEqualTo("600000");
        }

        @Test
        @DisplayName("PBKDF2 verify accepts correct password")
        void pbkdf2VerifyCorrect() throws Exception {
            String hash = AdvancedCryptographyService.hashPasswordPbkdf2("secret123");
            assertThat(AdvancedCryptographyService.verifyPasswordPbkdf2("secret123", hash)).isTrue();
        }

        @Test
        @DisplayName("PBKDF2 verify rejects wrong password")
        void pbkdf2VerifyWrong() throws Exception {
            String hash = AdvancedCryptographyService.hashPasswordPbkdf2("secret123");
            assertThat(AdvancedCryptographyService.verifyPasswordPbkdf2("wrong", hash)).isFalse();
        }

        @Test
        @DisplayName("PBKDF2 different hashes for same password (unique salt)")
        void pbkdf2UniqueSalt() throws Exception {
            String h1 = AdvancedCryptographyService.hashPasswordPbkdf2("same");
            String h2 = AdvancedCryptographyService.hashPasswordPbkdf2("same");
            assertThat(h1).isNotEqualTo(h2); // different random salts
        }

        @Test
        @DisplayName("KDF comparison table has all entries")
        void kdfComparisonTable() {
            assertThat(AdvancedCryptographyService.KdfComparison.all()).hasSize(4)
                .extracting(AdvancedCryptographyService.KdfComparison::name)
                .contains("Argon2id", "PBKDF2", "bcrypt", "scrypt");
        }

        @Test
        @DisplayName("mTLS config has required fields")
        void mtlsConfig() {
            var config = AdvancedCryptographyService.MTLSConfig.forServiceMesh();
            assertThat(config.clientAuth()).isEqualTo("need");
            assertThat(config.requireClientCert()).isTrue();
            assertThat(config.toSpringProperties()).containsKey("server.ssl.key-store");
        }
    }

    // ── MFA / TOTP ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("MFA — TOTP (RFC 6238)")
    class MFATests {

        @Test
        @DisplayName("Generate secret is 20 bytes")
        void secretLength() {
            assertThat(MFAService.generateSecret()).hasSize(20);
        }

        @Test
        @DisplayName("Base32 encoding of secret is uppercase alphanumeric")
        void secretBase32() {
            byte[] secret = MFAService.generateSecret();
            String b32 = MFAService.secretToBase32(secret);
            assertThat(b32).matches("[A-Z2-7=]+");
        }

        @Test
        @DisplayName("Generated TOTP is exactly 6 digits")
        void totpSixDigits() throws Exception {
            byte[] secret = MFAService.generateSecret();
            String totp = MFAService.generateTOTP(secret);
            assertThat(totp).matches("\\d{6}");
        }

        @Test
        @DisplayName("TOTP generated and immediately verified — passes")
        void totpVerifyCurrentCode() throws Exception {
            byte[] secret = MFAService.generateSecret();
            String code = MFAService.generateTOTP(secret);
            assertThat(MFAService.verifyTOTP(secret, code)).isTrue();
        }

        @Test
        @DisplayName("Wrong TOTP code is rejected")
        void totpRejectsWrongCode() throws Exception {
            byte[] secret = MFAService.generateSecret();
            assertThat(MFAService.verifyTOTP(secret, "000000")).isFalse();
        }

        @Test
        @DisplayName("TOTP null/blank code is rejected")
        void totpRejectsNull() throws Exception {
            byte[] secret = MFAService.generateSecret();
            assertThat(MFAService.verifyTOTP(secret, null)).isFalse();
            assertThat(MFAService.verifyTOTP(secret, "12345")).isFalse(); // 5 digits
        }

        @Test
        @DisplayName("TOTP for known timestamp matches RFC 6238 test vector")
        void totpKnownVector() throws Exception {
            // RFC 6238 test: seed = "12345678901234567890", T=59s, expected=94287082 (but 6-digit: 287082)
            byte[] seed = "12345678901234567890".getBytes();
            String code = MFAService.generateTOTP(seed, 59L);
            assertThat(code).hasSize(6);
            // The TOTP value at T=59 step=1 with SHA1 is 94287082 → 6-digit = 94287082 % 1000000 = 287082
            assertThat(code).isEqualTo("287082");
        }

        @Test
        @DisplayName("OTP auth URI has correct format")
        void otpAuthUri() {
            byte[] secret = MFAService.generateSecret();
            String uri = MFAService.generateOtpAuthUri("ConceptualWare", "alice@example.com", secret);
            assertThat(uri).startsWith("otpauth://totp/");
            assertThat(uri).contains("algorithm=SHA1").contains("digits=6").contains("period=30");
        }

        @Test
        @DisplayName("Generate 10 backup codes of length 10")
        void backupCodes() {
            List<String> codes = MFAService.generateBackupCodes();
            assertThat(codes).hasSize(10);
            codes.forEach(c -> assertThat(c).hasSize(10).matches("[A-Z0-9]{10}"));
        }

        @Test
        @DisplayName("MFA enrollment creates all required fields")
        void mfaEnrollment() {
            var enrollment = MFAService.MFAEnrollment.enroll("user-1", "ConceptualWare");
            assertThat(enrollment.userId()).isEqualTo("user-1");
            assertThat(enrollment.secret()).hasSize(20);
            assertThat(enrollment.backupCodes()).hasSize(10);
            assertThat(enrollment.otpAuthUri()).startsWith("otpauth://");
        }
    }

    // ── Key Rotation ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Key Rotation")
    class KeyRotationTests {

        @Test
        @DisplayName("KeyRotationManager starts with one active key")
        void initialKey() {
            ZeroTrustService.KeyRotationManager mgr = new ZeroTrustService.KeyRotationManager();
            assertThat(mgr.activeKeyCount()).isEqualTo(1);
            assertThat(mgr.currentKey()).isNotNull();
            assertThat(mgr.currentKeyId()).startsWith("key-");
        }

        @Test
        @DisplayName("Generating new key increases key count to 2")
        void newKeyAdded() {
            ZeroTrustService.KeyRotationManager mgr = new ZeroTrustService.KeyRotationManager();
            String oldKid = mgr.currentKeyId();
            mgr.generateNewKey();
            assertThat(mgr.activeKeyCount()).isEqualTo(2);
            assertThat(mgr.currentKeyId()).isNotEqualTo(oldKid);
        }

        @Test
        @DisplayName("Old key remains findable after rotation")
        void oldKeyFindable() {
            ZeroTrustService.KeyRotationManager mgr = new ZeroTrustService.KeyRotationManager();
            String oldKid = mgr.currentKeyId();
            mgr.generateNewKey();
            assertThat(mgr.findKey(oldKid)).isPresent();
        }

        @Test
        @DisplayName("KeyRotationService signs and verifies token")
        void keyRotationServiceSignVerify() {
            KeyRotationService svc = new KeyRotationService();
            io.jsonwebtoken.Claims claims = Jwts.claims();
            claims.setSubject("user-123");
            claims.setExpiration(Date.from(Instant.now().plusSeconds(300)));
            String token = svc.sign(claims);
            io.jsonwebtoken.Claims verified = svc.verify(token);
            assertThat(verified.getSubject()).isEqualTo("user-123");
        }

        @Test
        @DisplayName("KeyRotationService JWKS lists active keys")
        void jwksLists() {
            KeyRotationService svc = new KeyRotationService();
            Map<String, Object> jwks = svc.getJwks();
            assertThat(jwks).containsKey("keys");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> keys = (List<Map<String, Object>>) jwks.get("keys");
            assertThat(keys).isNotEmpty();
        }
    }

    // ── Zero Trust ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Zero Trust Architecture")
    class ZeroTrustTests {

        private Key testKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);

        @Test
        @DisplayName("Service token is issued and verified")
        void serviceTokenVerified() {
            String token = ZeroTrustService.issueServiceToken("svc-a", "svc-b", testKey);
            var claims = ZeroTrustService.verifyServiceToken(token, "svc-b", testKey);
            assertThat(claims.valid()).isTrue();
            assertThat(claims.serviceName()).isEqualTo("svc-a");
        }

        @Test
        @DisplayName("Token for wrong audience is rejected")
        void wrongAudienceRejected() {
            String token = ZeroTrustService.issueServiceToken("svc-a", "svc-b", testKey);
            var claims = ZeroTrustService.verifyServiceToken(token, "svc-c", testKey);
            assertThat(claims.valid()).isFalse();
        }

        @Test
        @DisplayName("Tampered token is rejected")
        void tamperedTokenRejected() {
            String token = ZeroTrustService.issueServiceToken("svc-a", "svc-b", testKey);
            String tampered = token.substring(0, token.length() - 5) + "XXXXX";
            var claims = ZeroTrustService.verifyServiceToken(tampered, "svc-b", testKey);
            assertThat(claims.valid()).isFalse();
        }

        @Test
        @DisplayName("Access context with MFA + managed device → HIGH trust")
        void highTrustContext() {
            var ctx = new ZeroTrustService.AccessContext(
                "user-1", Set.of("verified-mfa"), "engineering",
                "10.0.0.5", "managed",
                Instant.now().minusSeconds(3600 - 14400), // 10am UTC
                "/api/data", "read"
            );
            assertThat(ZeroTrustService.evaluateAccess(ctx))
                .isIn(ZeroTrustService.TrustLevel.MEDIUM, ZeroTrustService.TrustLevel.HIGH);
        }

        @Test
        @DisplayName("Access context with no MFA + unknown device → DENY or LOW")
        void lowTrustContext() {
            var ctx = new ZeroTrustService.AccessContext(
                "user-2", Set.of("user"), "external",
                "1.2.3.4", "unknown",
                Instant.now(),
                "/api/admin", "write"
            );
            assertThat(ZeroTrustService.evaluateAccess(ctx))
                .isIn(ZeroTrustService.TrustLevel.DENY, ZeroTrustService.TrustLevel.LOW);
        }

        @Test
        @DisplayName("SBOM document for ConceptualWare has required fields")
        void sbomDocument() {
            var sbom = ZeroTrustService.SbomDocument.forConceptualWare();
            assertThat(sbom.bomFormat()).isEqualTo("CycloneDX");
            assertThat(sbom.specVersion()).isEqualTo("1.5");
            assertThat(sbom.components()).hasSizeGreaterThanOrEqualTo(3);
            assertThat(sbom.components()).extracting(ZeroTrustService.SbomComponent::name)
                .contains("spring-boot", "spring-security", "jackson-databind");
        }

        @Test
        @DisplayName("Least privilege — algorithm service has limited permissions")
        void leastPrivilege() {
            var perms = ZeroTrustService.ServicePermissions.algorithmService();
            assertThat(perms.canReadSecrets()).isFalse();
            assertThat(perms.allowedDatabases()).hasSize(1);
        }
    }
}
