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

@Service
public class KeyRotationService {

    private static final Logger log = LoggerFactory.getLogger(KeyRotationService.class);

    private static final long   KEY_ROTATION_MS  = 7 * 24 * 60 * 60 * 1000L;
    private static final long   KEY_RETIRE_MS    = 25 * 60 * 60 * 1000L;

    private volatile String     currentKeyId;
    private volatile Key        currentKey;

    private final ConcurrentHashMap<String, KeyEntry> keyRing = new ConcurrentHashMap<>();

    public KeyRotationService() {
        rotateKey();
    }

    private record KeyEntry(String kid, Key key, Instant createdAt, Instant retireAt) {
        boolean isExpired(Instant now) { return retireAt.isBefore(now); }
    }

    @Scheduled(fixedDelayString = "${security.key-rotation-ms:604800000}")
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

        retireExpiredKeys();
    }

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

    public String sign(Claims claims) {
        return Jwts.builder()
            .setClaims(claims)
            .setHeaderParam("kid", currentKeyId)
            .signWith(currentKey, SignatureAlgorithm.HS256)
            .compact();
    }

    public Claims verify(String token) {
        String kid = extractKid(token);

        KeyEntry entry = kid != null ? keyRing.get(kid) : null;
        Key verifyKey  = entry != null ? entry.key() : currentKey;

        return Jwts.parser()
            .verifyWith((javax.crypto.SecretKey) verifyKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private String extractKid(String token) {
        try {
            String[] parts  = token.split("\\.");
            if (parts.length < 2) return null;
            String header   = new String(Base64.getUrlDecoder().decode(parts[0]));
            int kidStart = header.indexOf("\"kid\":\"");
            if (kidStart == -1) return null;
            kidStart += 7;
            int kidEnd = header.indexOf("\"", kidStart);
            return kidEnd > kidStart ? header.substring(kidStart, kidEnd) : null;
        } catch (Exception e) { return null; }
    }

    public Map<String, Object> getJwks() {
        List<Map<String, Object>> keys = keyRing.entrySet().stream()
            .filter(e -> !e.getValue().isExpired(Instant.now()))
            .map(e -> Map.<String, Object>of(
                "kid", e.getKey(),
                "kty", "oct",
                "alg", "HS256",
                "use", "sig",
                "active", e.getKey().equals(currentKeyId)
            ))
            .toList();

        return Map.of("keys", keys);
    }

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
