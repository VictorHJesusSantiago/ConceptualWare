package com.conceptualware.security;

import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Concept #21 — Key Rotation (Rotação de Chaves Criptográficas):
 *
 *   Why rotate keys?
 *     - Limits exposure window if a key is compromised.
 *     - NIST SP 800-57: key usage period should be limited by cryptoperiod.
 *     - PCI DSS 3.6.4: cryptographic keys must be changed at least annually.
 *     - Most security policies: 90 days for signing keys.
 *
 *   Key rotation strategy for JWT:
 *     1. Generate new key K2.
 *     2. Start signing new tokens with K2.
 *     3. Keep K1 in "retired" set for verification (tokens expire in up to 24h).
 *     4. After token TTL expires, remove K1 from verification set.
 *     5. Publish public keys at /.well-known/jwks.json (for asymmetric keys).
 *
 *   JWKS (JSON Web Key Set):
 *     Public endpoint that lists all currently valid public keys (RSA/EC).
 *     Services fetch JWKS to verify tokens without needing the private key.
 *     Key identified by "kid" (Key ID) claim in JWT header.
 *
 *   Zero-downtime rotation:
 *     - Transition period: both old and new keys valid simultaneously.
 *     - No client token expiration during rotation.
 *
 * Concept #21 — Key management, cryptographic lifecycle, secret rotation
 */
@Service
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

    private static final long   KEY_ROTATION_MS  = 7 * 24 * 60 * 60 * 1000L; // 7 days (shorter for demo)
    private static final long   KEY_RETIRE_MS    = 25 * 60 * 60 * 1000L;      // 25 hours (token TTL + buffer)

    private volatile String     currentKeyId;
    private volatile Key        currentKey;

    // All active keys indexed by kid — ConcurrentHashMap for thread safety
    private final ConcurrentHashMap<String, KeyEntry> keyRing = new ConcurrentHashMap<>();

    public KeyRotationService() {
        rotateKey(); // generate initial key on startup
    }

    // ── Key Entry ─────────────────────────────────────────────────────────────

    private record KeyEntry(String kid, Key key, Instant createdAt, Instant retireAt) {
        boolean isExpired(Instant now) { return retireAt.isBefore(now); }
    }

    // ── Key Generation ────────────────────────────────────────────────────────

    /**
     * Generate a new signing key and make it the current key.
     * Old key remains in the ring for verification during the transition period.
     * Scheduled: runs every KEY_ROTATION_MS to rotate keys automatically.
     */
    @Scheduled(fixedDelayString = "${security.key-rotation-ms:604800000}") // 7 days default
    public synchronized void rotateKey() {
        String kid   = "key-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
        Key    newKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
        Instant now  = Instant.now();

        KeyEntry entry = new KeyEntry(kid, newKey, now, now.plusMillis(KEY_RETIRE_MS));
        keyRing.put(kid, entry);

        String previousKeyId = currentKeyId;
        currentKeyId = kid;
        currentKey   = newKey;

        log.info("Key rotated: new_kid={} previous_kid={} keyring_size={}", kid, previousKeyId, keyRing.size());

        // Retire expired keys in the same pass
        retireExpiredKeys();
    }

    /** Remove keys whose retire-at time has passed. */
    private void retireExpiredKeys() {
        Instant now = Instant.now();
        List<String> toRemove = keyRing.entrySet().stream()
            .filter(e -> e.getValue().isExpired(now) && !e.getKey().equals(currentKeyId))
            .map(Map.Entry::getKey)
            .toList();

        toRemove.forEach(kid -> {
            keyRing.remove(kid);
            log.info("Retired key: kid={}", kid);
        });
    }

    // ── Token Signing ─────────────────────────────────────────────────────────

    /** Sign a JWT with the current key. Includes kid in header for efficient verification. */
    public String sign(Claims claims) {
        return Jwts.builder()
            .setClaims(claims)
            .setHeaderParam("kid", currentKeyId) // enables O(1) key lookup during verification
            .signWith(currentKey, SignatureAlgorithm.HS256)
            .compact();
    }

    // ── Token Verification ────────────────────────────────────────────────────

    /**
     * Verify a JWT using the key identified by the kid header.
     * Supports tokens signed with any non-retired key — enables zero-downtime rotation.
     */
    public Claims verify(String token) {
        // Extract kid from header without validating signature (safe — we only use it for key lookup)
        String kid = extractKid(token);

        KeyEntry entry = kid != null ? keyRing.get(kid) : null;
        Key verifyKey  = entry != null ? entry.key() : currentKey;

        return Jwts.parserBuilder()
            .setSigningKey(verifyKey)
            .build()
            .parseClaimsJws(token)
            .getBody();
    }

    private String extractKid(String token) {
        try {
            String[] parts  = token.split("\\.");
            if (parts.length < 2) return null;
            String header   = new String(Base64.getUrlDecoder().decode(parts[0]));
            // Simple extraction — production would use Jackson
            int kidStart = header.indexOf("\"kid\":\"");
            if (kidStart == -1) return null;
            kidStart += 7;
            int kidEnd = header.indexOf("\"", kidStart);
            return kidEnd > kidStart ? header.substring(kidStart, kidEnd) : null;
        } catch (Exception e) { return null; }
    }

    // ── JWKS Endpoint Data ────────────────────────────────────────────────────

    /**
     * JWKS (JSON Web Key Set) — data for /.well-known/jwks.json.
     * For symmetric keys (HS256): key material is SECRET — never publish in JWKS.
     * For asymmetric keys (RS256): publish the PUBLIC key only.
     *
     * This method returns metadata only (no key material) for symmetric keys.
     */
    public Map<String, Object> getJwks() {
        List<Map<String, Object>> keys = keyRing.entrySet().stream()
            .filter(e -> !e.getValue().isExpired(Instant.now()))
            .map(e -> Map.<String, Object>of(
                "kid", e.getKey(),
                "kty", "oct",         // key type: symmetric
                "alg", "HS256",
                "use", "sig",         // key use: signature
                "active", e.getKey().equals(currentKeyId)
            ))
            .toList();

        return Map.of("keys", keys);
    }

    // ── Key rotation status ───────────────────────────────────────────────────

    public record KeyRotationStatus(
        String  currentKeyId,
        Instant currentKeyCreated,
        int     totalActiveKeys,
        Instant nextRotation
    ) {}

    public KeyRotationStatus getStatus() {
        KeyEntry current = keyRing.get(currentKeyId);
        return new KeyRotationStatus(
            currentKeyId,
            current != null ? current.createdAt() : null,
            keyRing.size(),
            current != null ? current.createdAt().plusMillis(KEY_ROTATION_MS) : null
        );
    }

    public Key getCurrentKey() { return currentKey; }
    public String getCurrentKeyId() { return currentKeyId; }
}
