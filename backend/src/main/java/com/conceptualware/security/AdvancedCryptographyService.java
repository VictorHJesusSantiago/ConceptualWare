package com.conceptualware.security;

import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/**
 * Concept #21 — Advanced Cryptography:
 *
 *   Argon2 — memory-hard password hashing (winner of Password Hashing Competition 2015).
 *     Variants: Argon2d (GPU resistance), Argon2i (side-channel resistance), Argon2id (both).
 *     Parameters: time (iterations), memory (KB), parallelism (threads).
 *     Java: Bouncy Castle (org.bouncycastle:bcprov-jdk18on) or Spring Security Argon2PasswordEncoder.
 *
 *   PBKDF2 — Password-Based Key Derivation Function 2 (RFC 8018).
 *     Iterations should be ≥ 600,000 (NIST SP 800-132 recommendation for SHA-256).
 *     Java: SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").
 *
 *   scrypt — memory-hard KDF (Colin Percival, 2009).
 *     Parameters: N (CPU/memory cost), r (block size), p (parallelism).
 *     Java: Bouncy Castle SCryptPasswordEncoder.
 *
 *   AES-GCM — authenticated encryption (already in CryptographicAlgorithms).
 *
 *   RSA-OAEP — OAEP padding for RSA (already in CryptographicAlgorithms).
 *
 * Note: Argon2 and scrypt require Bouncy Castle. If not available, we fall back to
 *       PBKDF2 (which is in JDK) and document the Bouncy Castle equivalents.
 *
 * Concept #21 — Password security, key derivation, memory-hard hashing
 */
@Service
public class AdvancedCryptographyService {

    // ── PBKDF2 Password Hashing (JDK built-in) ───────────────────────────────

    private static final int PBKDF2_ITERATIONS = 600_000; // NIST 2023 recommendation
    private static final int PBKDF2_KEY_LEN    = 256;     // bits
    private static final int SALT_BYTES        = 32;

    public static String hashPasswordPbkdf2(String password) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LEN);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = skf.generateSecret(spec).getEncoded();
        spec.clearPassword();

        // Store: iterations$salt$hash (all base64)
        return PBKDF2_ITERATIONS + "$" + base64(salt) + "$" + base64(hash);
    }

    public static boolean verifyPasswordPbkdf2(String password, String stored) throws Exception {
        String[] parts   = stored.split("\\$");
        int      iters   = Integer.parseInt(parts[0]);
        byte[]   salt    = Base64.getDecoder().decode(parts[1]);
        byte[]   expected = Base64.getDecoder().decode(parts[2]);

        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, iters, expected.length * 8);
        byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).getEncoded();
        spec.clearPassword();

        return MessageDigest.isEqual(expected, actual); // constant-time comparison
    }

    // ── Argon2 (documented — requires Bouncy Castle at runtime) ──────────────

    /**
     * Argon2id configuration (OWASP recommendation):
     *   Memory: 19 MB (19456 KB), Iterations: 2, Parallelism: 1
     *   Minimum for OWASP: 15 MB / 2 iterations / 1 lane.
     *
     * If Bouncy Castle is present on classpath, this method uses it.
     * Otherwise it falls back to PBKDF2 and logs a warning.
     */
    public static String hashPasswordArgon2id(String password) throws Exception {
        try {
            // Try Bouncy Castle Argon2
            Class<?> argon2Class = Class.forName("org.bouncycastle.crypto.generators.Argon2BytesGenerator");
            Class<?> paramsClass = Class.forName("org.bouncycastle.crypto.params.Argon2Parameters");
            Class<?> builderClass = Class.forName("org.bouncycastle.crypto.params.Argon2Parameters$Builder");

            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            // Argon2id = type 2
            Object builder = builderClass.getDeclaredConstructor(int.class).newInstance(2);
            builderClass.getMethod("withSalt", byte[].class).invoke(builder, salt);
            builderClass.getMethod("withMemoryAsKB", int.class).invoke(builder, 19456); // 19MB
            builderClass.getMethod("withIterations", int.class).invoke(builder, 2);
            builderClass.getMethod("withParallelism", int.class).invoke(builder, 1);
            Object params = builderClass.getMethod("build").invoke(builder);

            Object generator = argon2Class.getDeclaredConstructor().newInstance();
            argon2Class.getMethod("init", paramsClass).invoke(generator, params);

            byte[] passwordBytes = password.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] hash = new byte[32];
            argon2Class.getMethod("generateBytes", byte[].class, byte[].class, int.class, int.class)
                .invoke(generator, passwordBytes, hash, 0, hash.length);

            return "argon2id$19456$2$1$" + base64(salt) + "$" + base64(hash);

        } catch (ClassNotFoundException e) {
            // Bouncy Castle not available — fall back to PBKDF2
            return "pbkdf2-fallback$" + hashPasswordPbkdf2(password);
        }
    }

    // ── Argon2 / scrypt conceptual parameters ─────────────────────────────────

    public record KdfComparison(
        String name, String memory, String time, String parallelism,
        String javaLibrary, String owasp2023Recommendation
    ) {
        public static KdfComparison[] all() {
            return new KdfComparison[]{
                new KdfComparison("Argon2id",  "19MB min",  "2 iterations", "1 lane",
                    "Bouncy Castle, Spring Security", "FIRST CHOICE (memory-hard, GPU resistant)"),
                new KdfComparison("scrypt",    "128MB (N=2^17)", "r=8, p=1", "1",
                    "Bouncy Castle",                 "Alternative to Argon2"),
                new KdfComparison("PBKDF2",    "Minimal",   "600k iters",   "1",
                    "JDK (built-in)",                "Use when memory-hard not possible"),
                new KdfComparison("bcrypt",    "4KB",       "cost=12",      "1",
                    "Spring Security",               "Legacy — prefer Argon2id for new apps"),
            };
        }
    }

    // ── mTLS (Mutual TLS) Configuration ──────────────────────────────────────

    /**
     * mTLS: both client AND server present X.509 certificates.
     * Server authenticates client's certificate (in addition to normal TLS).
     *
     * Spring Boot configuration (application.yml):
     *   server:
     *     ssl:
     *       key-store: classpath:keystore.p12
     *       key-store-password: ${SSL_KEYSTORE_PASSWORD}
     *       key-store-type: PKCS12
     *       trust-store: classpath:truststore.p12   ← validates client certs
     *       trust-store-password: ${SSL_TRUSTSTORE_PASSWORD}
     *       client-auth: need                        ← "need" = require mTLS, "want" = optional
     *
     * Client must present a certificate signed by a CA in the server's truststore.
     * Used in: service mesh (Istio), zero trust networks, API-to-API authentication.
     */
    public record MTLSConfig(
        String keystorePath, String keystoreType, String clientAuth,
        String trustStorePath, boolean requireClientCert
    ) {
        public static MTLSConfig forServiceMesh() {
            return new MTLSConfig("classpath:tls/keystore.p12", "PKCS12", "need",
                "classpath:tls/truststore.p12", true);
        }

        public Map<String, Object> toSpringProperties() {
            return Map.of(
                "server.ssl.key-store",             keystorePath,
                "server.ssl.key-store-type",        keystoreType,
                "server.ssl.client-auth",           clientAuth,
                "server.ssl.trust-store",           trustStorePath,
                "server.ssl.trust-store-type",      "PKCS12"
            );
        }
    }

    // ── X.509 Certificate operations ─────────────────────────────────────────

    /** Generate a self-signed certificate keypair for testing. */
    public static KeyPair generateKeyPairForCert() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        return kpg.generateKeyPair();
    }

    // ── AES-GCM with Additional Authenticated Data ────────────────────────────

    /** AES-256-GCM with AAD — associated data is authenticated but not encrypted. */
    public static EncryptedData encryptWithAAD(byte[] plaintext, SecretKey key, byte[] aad)
        throws Exception {
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        if (aad != null) cipher.updateAAD(aad);

        byte[] ciphertext = cipher.doFinal(plaintext);
        return new EncryptedData(iv, ciphertext, aad);
    }

    public record EncryptedData(byte[] iv, byte[] ciphertext, byte[] aad) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
