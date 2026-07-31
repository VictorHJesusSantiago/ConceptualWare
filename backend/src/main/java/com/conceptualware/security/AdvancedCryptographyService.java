package com.conceptualware.security;

import org.springframework.stereotype.Service;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

@Service
public class AdvancedCryptographyService {

    private static final int PBKDF2_ITERATIONS = 600_000;
    private static final int PBKDF2_KEY_LEN    = 256;
    private static final int SALT_BYTES        = 32;

    public static String hashPasswordPbkdf2(String password) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);

        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LEN);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = skf.generateSecret(spec).getEncoded();
        spec.clearPassword();

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

        return MessageDigest.isEqual(expected, actual);
    }

    public static String hashPasswordArgon2id(String password) throws Exception {
        try {
            Class<?> argon2Class = Class.forName("org.bouncycastle.crypto.generators.Argon2BytesGenerator");
            Class<?> paramsClass = Class.forName("org.bouncycastle.crypto.params.Argon2Parameters");
            Class<?> builderClass = Class.forName("org.bouncycastle.crypto.params.Argon2Parameters$Builder");

            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);

            Object builder = builderClass.getDeclaredConstructor(int.class).newInstance(2);
            builderClass.getMethod("withSalt", byte[].class).invoke(builder, salt);
            builderClass.getMethod("withMemoryAsKB", int.class).invoke(builder, 19456);
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
            return "pbkdf2-fallback$" + hashPasswordPbkdf2(password);
        }
    }

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

    public static KeyPair generateKeyPairForCert() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        return kpg.generateKeyPair();
    }

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

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
