package com.conceptualware.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.*;

@Service
public class ZeroTrustService {

    private static final Logger log = LoggerFactory.getLogger(ZeroTrustService.class);

    public static String issueServiceToken(String serviceName, String audience, Key signingKey) {
        Instant now    = Instant.now();
        Instant expiry = now.plusSeconds(300);

        return Jwts.builder()
            .setSubject(serviceName)
            .setAudience(audience)
            .setIssuedAt(Date.from(now))
            .setExpiration(Date.from(expiry))
            .setId(UUID.randomUUID().toString())
            .claim("type",    "service")
            .claim("version", "1.0")
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact();
    }

    public static ServiceTokenClaims verifyServiceToken(String token, String expectedAudience,
                                                          Key verifyKey) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) verifyKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (!claims.getAudience().contains(expectedAudience)) {
                return ServiceTokenClaims.failed("Audience mismatch — expected " + expectedAudience);
            }
            if (!"service".equals(claims.get("type", String.class))) {
                return ServiceTokenClaims.failed("Token type mismatch — expected service token");
            }

            String audience = claims.getAudience().stream().findFirst().orElse(null);

            return new ServiceTokenClaims(
                claims.getSubject(),
                audience,
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

    public static class KeyRotationManager {

        private record KeyVersion(String keyId, Key key, Instant createdAt, Instant retireAt) {}

        private final Deque<KeyVersion> keys = new ArrayDeque<>();
        private static final int MAX_KEY_AGE_DAYS = 90;
        private final java.util.concurrent.atomic.AtomicLong keySeq = new java.util.concurrent.atomic.AtomicLong();

        public KeyRotationManager() {
            generateNewKey();
        }

        public synchronized void generateNewKey() {
            Key newKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
            String keyId = "key-" + Instant.now().toEpochMilli() + "-" + keySeq.incrementAndGet();
            Instant now  = Instant.now();
            keys.addFirst(new KeyVersion(keyId, newKey, now, now.plusSeconds(86400L * MAX_KEY_AGE_DAYS)));
            log.info("New signing key generated: {}", keyId);
        }

        public synchronized Key currentKey() {
            if (keys.isEmpty()) generateNewKey();
            return keys.peekFirst().key();
        }

        public synchronized String currentKeyId() {
            if (keys.isEmpty()) generateNewKey();
            return keys.peekFirst().keyId();
        }

        public synchronized Optional<Key> findKey(String keyId) {
            return keys.stream()
                .filter(k -> k.keyId().equals(keyId))
                .map(KeyVersion::key)
                .findFirst();
        }

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

    public record AccessContext(
        String userId,
        Set<String> roles,
        String department,
        String ipAddress,
        String deviceTrustLevel,
        Instant requestTime,
        String resource,
        String action
    ) {}

    public enum TrustLevel { DENY, LOW, MEDIUM, HIGH }

    public static TrustLevel evaluateAccess(AccessContext ctx) {
        int score = 0;

        if (isInternalIp(ctx.ipAddress())) score += 1;

        if (ctx.roles().contains("verified-mfa")) score += 2;

        score += switch (ctx.deviceTrustLevel()) {
            case "managed"  -> 3;
            case "personal" -> 1;
            default         -> 0;
        };

        int hour = ctx.requestTime().atZone(java.time.ZoneId.of("UTC")).getHour();
        if (hour >= 8 && hour <= 18) score += 1;

        if      (score >= 6) return TrustLevel.HIGH;
        else if (score >= 4) return TrustLevel.MEDIUM;
        else if (score >= 2) return TrustLevel.LOW;
        else                 return TrustLevel.DENY;
    }

    private static boolean isInternalIp(String ip) {
        if (ip == null) return false;
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.");
    }

    public record SbomComponent(
        String name,
        String version,
        String type,
        String license,
        String purl,
        List<String> knownCves
    ) {}

    public record SbomDocument(
        String bomFormat,
        String specVersion,
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

    public record ServicePermissions(
        String serviceName,
        Set<String> allowedEndpoints,
        Set<String> allowedDatabases,
        Set<String> allowedTopics,
        boolean canWriteToDatabase,
        boolean canReadSecrets
    ) {
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
