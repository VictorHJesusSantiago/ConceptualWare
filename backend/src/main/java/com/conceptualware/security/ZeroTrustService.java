package com.conceptualware.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.*;

/**
 * Concept #21 — Zero Trust Architecture:
 *
 *   Zero Trust Principles (BeyondCorp, NIST SP 800-207):
 *     1. "Never trust, always verify" — no implicit trust based on network location.
 *     2. Assume breach — treat every request as if from an untrusted network.
 *     3. Least privilege — each service gets only minimum permissions needed.
 *     4. Verify explicitly — authenticate and authorize every request.
 *     5. Use strong identity — certificates, JWTs, mTLS for service-to-service.
 *
 *   Service-to-Service Authentication:
 *     - Each service has its own identity (JWT or mTLS certificate).
 *     - Service A → Service B: A presents its JWT, B validates signature + claims.
 *     - Never hardcode credentials; use SPIFFE/SPIRE or Vault for dynamic creds.
 *
 *   Context-aware access (ABAC vs RBAC):
 *     RBAC: access based on role (admin, user, readonly).
 *     ABAC: access based on attributes (role + department + time + location + device health).
 *
 *   Key Rotation:
 *     - JWT signing keys must be rotated regularly (NIST recommends ≤ 90 days).
 *     - JWKS (JSON Web Key Set): publish public keys at /.well-known/jwks.json.
 *     - Rotation: generate new key → sign new tokens → keep old key for verification
 *       during transition → retire old key after all old tokens expire.
 *
 *   SBOM (Software Bill of Materials):
 *     - Inventory of all components in your software (libraries, licenses, versions).
 *     - Formats: CycloneDX (JSON), SPDX (text/JSON).
 *     - Required by US Executive Order 14028 (May 2021) for federal software.
 *
 * Concept #21 — Zero Trust, key management, supply chain security
 */
@Service
public class ZeroTrustService {

    private static final Logger log = LoggerFactory.getLogger(ZeroTrustService.class);

    // ── Service Identity JWT ──────────────────────────────────────────────────

    /**
     * Service token: short-lived JWT used for service-to-service authentication.
     * Issued by a central identity authority (like HashiCorp Vault or AWS IAM).
     */
    public static String issueServiceToken(String serviceName, String audience, Key signingKey) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(300); // 5-minute token — short-lived by design

        return Jwts.builder()
            .setSubject(serviceName)
            .setAudience(audience)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .setId(UUID.randomUUID().toString()) // unique JTI for replay prevention
            .claim("type",    "service")
            .claim("version", "1.0")
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * Verify a service token: "never trust, always verify."
     * Validates: signature, expiry, audience, issuer, type claim.
     */
    public static ServiceTokenClaims verifyServiceToken(String token, String expectedAudience,
                                                          Key verifyKey) {
        try {
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(verifyKey)
                .requireAudience(expectedAudience)
                .build()
                .parseClaimsJws(token)
                .getBody();

            if (!"service".equals(claims.get("type", String.class))) {
                throw new SecurityException("Token type mismatch — expected service token");
            }

            return new ServiceTokenClaims(
                claims.getSubject(),
                claims.getAudience(),
                claims.getExpiration().toInstant(),
                claims.getId(),
                true,
                null
            );

        } catch (ExpiredJwtException e) {
            return ServiceTokenClaims.failed("Token expired");
        } catch (JwtException e) {
            return ServiceTokenClaims.failed("Invalid token: " + e.getMessage());
        }
    }

    public record ServiceTokenClaims(
        String  serviceName,
        String  audience,
        Instant expiry,
        String  tokenId,
        boolean valid,
        String  errorReason
    ) {
        static ServiceTokenClaims failed(String reason) {
            return new ServiceTokenClaims(null, null, null, null, false, reason);
        }
    }

    // ── Key Rotation ──────────────────────────────────────────────────────────

    /**
     * Key rotation manager: maintains multiple keys (current + previous).
     * - Current key: signs new tokens.
     * - Previous key(s): still valid for verifying tokens issued before rotation.
     * - Keys are retired after their associated tokens expire.
     */
    public static class KeyRotationManager {

        private record KeyVersion(String keyId, Key key, Instant createdAt, Instant retireAt) {}

        private final Deque<KeyVersion> keys = new ArrayDeque<>();
        private static final int MAX_KEY_AGE_DAYS = 90;

        public synchronized void generateNewKey() {
            Key newKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            String keyId = "key-" + Instant.now().toEpochMilli();
            Instant now  = Instant.now();
            keys.addFirst(new KeyVersion(keyId, newKey, now, now.plusSeconds(86400L * MAX_KEY_AGE_DAYS)));
            log.info("New signing key generated: {}", keyId);
        }

        /** Current key for signing new tokens. */
        public synchronized Key currentKey() {
            if (keys.isEmpty()) generateNewKey();
            return keys.peekFirst().key();
        }

        public synchronized String currentKeyId() {
            if (keys.isEmpty()) generateNewKey();
            return keys.peekFirst().keyId();
        }

        /** Find key by ID for verification (allows verifying tokens from previous key). */
        public synchronized Optional<Key> findKey(String keyId) {
            return keys.stream()
                .filter(k -> k.keyId().equals(keyId))
                .map(KeyVersion::key)
                .findFirst();
        }

        /** Retire expired keys (called by @Scheduled task). */
        public synchronized void retireExpiredKeys() {
            Instant now = Instant.now();
            int removed = 0;
            while (keys.size() > 1 && keys.peekLast().retireAt().isBefore(now)) {
                KeyVersion retired = keys.pollLast();
                log.info("Retired signing key: {}", retired.keyId());
                removed++;
            }
            if (removed > 0) log.info("Retired {} expired signing keys", removed);
        }

        public synchronized int activeKeyCount() { return keys.size(); }
    }

    // ── ABAC (Attribute-Based Access Control) ─────────────────────────────────

    public record AccessContext(
        String userId,
        Set<String> roles,
        String department,
        String ipAddress,
        String deviceTrustLevel,  // "managed", "personal", "unknown"
        Instant requestTime,
        String resource,
        String action
    ) {}

    public enum TrustLevel { DENY, LOW, MEDIUM, HIGH }

    /**
     * Zero Trust access evaluation: combines multiple signals to determine access.
     * More signals = higher trust level = more access allowed.
     */
    public static TrustLevel evaluateAccess(AccessContext ctx) {
        int score = 0;

        // Network risk signals
        if (isInternalIp(ctx.ipAddress())) score += 1;  // internal ≠ trusted in ZT, but lowers risk slightly

        // Identity strength
        if (ctx.roles().contains("verified-mfa")) score += 2;

        // Device trust
        score += switch (ctx.deviceTrustLevel()) {
            case "managed"  -> 3; // corporate MDM-enrolled device
            case "personal" -> 1; // BYOD with Endpoint Protection
            default         -> 0; // unknown device
        };

        // Time-of-day risk (simple example — production would use ML)
        int hour = ctx.requestTime().atZone(java.time.ZoneId.of("UTC")).getHour();
        if (hour >= 8 && hour <= 18) score += 1; // business hours

        // Map score to trust level
        if      (score >= 6) return TrustLevel.HIGH;
        else if (score >= 4) return TrustLevel.MEDIUM;
        else if (score >= 2) return TrustLevel.LOW;
        else                 return TrustLevel.DENY;
    }

    private static boolean isInternalIp(String ip) {
        if (ip == null) return false;
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.");
    }

    // ── SBOM Generation ───────────────────────────────────────────────────────

    /**
     * SBOM (Software Bill of Materials) in CycloneDX format.
     * Lists all dependencies with name, version, license, and vulnerability info.
     * Required for: US Federal Software (EO 14028), EU Cyber Resilience Act.
     */
    public record SbomComponent(
        String name,
        String version,
        String type,      // "library", "framework", "runtime"
        String license,
        String purl,      // Package URL: pkg:maven/group:artifact@version
        List<String> knownCves
    ) {}

    public record SbomDocument(
        String bomFormat,     // "CycloneDX"
        String specVersion,   // "1.5"
        String serialNumber,
        Instant timestamp,
        String applicationName,
        String applicationVersion,
        List<SbomComponent> components
    ) {
        public static SbomDocument forConceptualWare() {
            return new SbomDocument(
                "CycloneDX", "1.5",
                "urn:uuid:" + UUID.randomUUID(),
                Instant.now(),
                "ConceptualWare",
                "1.0.0",
                List.of(
                    new SbomComponent("spring-boot", "3.2.5", "framework",
                        "Apache-2.0", "pkg:maven/org.springframework.boot:spring-boot@3.2.5", List.of()),
                    new SbomComponent("spring-security", "6.2.4", "library",
                        "Apache-2.0", "pkg:maven/org.springframework.security:spring-security-core@6.2.4", List.of()),
                    new SbomComponent("mongodb-driver", "5.0.0", "library",
                        "Apache-2.0", "pkg:maven/org.mongodb:mongodb-driver-sync@5.0.0", List.of()),
                    new SbomComponent("jackson-databind", "2.17.0", "library",
                        "Apache-2.0", "pkg:maven/com.fasterxml.jackson.core:jackson-databind@2.17.0", List.of()),
                    new SbomComponent("jjwt", "0.11.5", "library",
                        "Apache-2.0", "pkg:maven/io.jsonwebtoken:jjwt@0.11.5", List.of())
                )
            );
        }

        public String toCycloneDxJson(com.fasterxml.jackson.databind.ObjectMapper mapper) {
            try { return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this); }
            catch (Exception e) { return "{}"; }
        }
    }

    // ── Least Privilege Principle ─────────────────────────────────────────────

    public record ServicePermissions(
        String serviceName,
        Set<String> allowedEndpoints,
        Set<String> allowedDatabases,
        Set<String> allowedTopics,    // Kafka/message bus topics
        boolean canWriteToDatabase,
        boolean canReadSecrets        // access to Vault/secrets manager
    ) {
        /** Algorithm service: read-only, only access its own DB collection. */
        public static ServicePermissions algorithmService() {
            return new ServicePermissions(
                "algorithm-service",
                Set.of("/api/algorithms/**", "/actuator/health", "/actuator/prometheus"),
                Set.of("conceptualware.algorithms"),
                Set.of("algorithm.events"),
                true,
                false
            );
        }
    }
}
