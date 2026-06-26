package com.conceptualware.core.algorithms;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/**
 * Concept #5 — Cryptographic Algorithms (Algoritmos Criptográficos):
 *
 *   AES (Advanced Encryption Standard) — symmetric block cipher, 128-bit blocks.
 *     Modes: ECB (insecure, no IV), CBC (needs IV), GCM (authenticated encryption).
 *     Java: javax.crypto.Cipher with "AES/GCM/NoPadding".
 *
 *   RSA — asymmetric encryption based on integer factorization.
 *     Key pair: public (n, e) for encryption, private (n, d) for decryption.
 *     Java: java.security.KeyPairGenerator + Cipher "RSA/ECB/OAEPWithSHA-256AndMGF1Padding".
 *
 *   SHA-256 — cryptographic hash function (SHA-2 family, 256-bit output).
 *     Properties: collision resistant, preimage resistant, avalanche effect.
 *     Java: java.security.MessageDigest.
 *
 *   HMAC-SHA256 — keyed hash for message authentication.
 *     Java: javax.crypto.Mac with "HmacSHA256".
 *
 *   PBKDF2 — Password-Based Key Derivation Function 2 (RFC 8018).
 *     Stretches passwords with salt + iterations to resist brute force.
 *     Java: SecretKeyFactory with "PBKDF2WithHmacSHA256".
 *
 * Concept #21 — Security: these implementations also appear in AdvancedCryptographyService
 * Concept #5  — Algorithm analysis: complexity is expressed in security bits
 */
public class CryptographicAlgorithms {

    // ── AES-GCM (Authenticated Encryption) ────────────────────────────────────

    public static final int AES_KEY_BITS = 256;
    public static final int GCM_IV_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;

    public static SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS, new SecureRandom());
        return kg.generateKey();
    }

    /**
     * AES-GCM encryption — authenticated encryption with associated data (AEAD).
     * GCM provides both confidentiality AND integrity (unlike CBC which needs separate HMAC).
     * @return IV prepended to ciphertext (IV is not secret but must be unique per key)
     */
    public static byte[] aesGcmEncrypt(byte[] plaintext, SecretKey key) throws Exception {
        byte[] iv = new byte[GCM_IV_BYTES];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));

        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] result     = new byte[iv.length + ciphertext.length];
        System.arraycopy(iv, 0, result, 0, iv.length);
        System.arraycopy(ciphertext, 0, result, iv.length, ciphertext.length);
        return result;
    }

    public static byte[] aesGcmDecrypt(byte[] ivAndCiphertext, SecretKey key) throws Exception {
        byte[] iv         = Arrays.copyOfRange(ivAndCiphertext, 0, GCM_IV_BYTES);
        byte[] ciphertext = Arrays.copyOfRange(ivAndCiphertext, GCM_IV_BYTES, ivAndCiphertext.length);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(ciphertext);
    }

    // ── RSA (2048-bit, OAEP padding) ──────────────────────────────────────────

    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        return kpg.generateKeyPair();
    }

    /**
     * RSA encryption with OAEP padding (PKCS#1 v2).
     * OAEP is semantically secure (same plaintext → different ciphertext due to random padding).
     * Never use RSA/ECB/PKCS1Padding for new code — PKCS#1 v1.5 has padding oracle vulnerabilities.
     */
    public static byte[] rsaEncrypt(byte[] data, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(data);
    }

    public static byte[] rsaDecrypt(byte[] ciphertext, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(ciphertext);
    }

    /** RSA digital signature with SHA-256 (used for non-repudiation). */
    public static byte[] rsaSign(byte[] data, PrivateKey privateKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(privateKey, new SecureRandom());
        sig.update(data);
        return sig.sign();
    }

    public static boolean rsaVerify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }

    // ── SHA-256 ───────────────────────────────────────────────────────────────

    /**
     * SHA-256: Secure Hash Algorithm — 256-bit (32 byte) output.
     * Properties:
     *   - Deterministic: same input → same hash
     *   - Avalanche effect: 1 bit change → ~50% of output bits change
     *   - Collision resistance: 2^128 operations to find collision (birthday paradox)
     *   - Preimage resistance: 2^256 operations to find input given hash
     */
    public static String sha256Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    public static byte[] sha256Bytes(byte[] input) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    /** SHA-512 for contexts requiring 512-bit security. */
    public static String sha512Hex(String input) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-512")
            .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    // ── HMAC-SHA256 (Message Authentication Code) ─────────────────────────────

    /**
     * HMAC: Keyed-Hash Message Authentication Code.
     * Verifies both data integrity and authentication (unlike plain SHA-256 which only verifies integrity).
     * Used in: JWT signatures, AWS SigV4, webhook verification.
     */
    public static String hmacSha256(String message, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return bytesToHex(mac.doFinal(message.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    // ── PBKDF2 (Password-Based Key Derivation) ────────────────────────────────

    /**
     * PBKDF2WithHmacSHA256: derives a cryptographic key from a password.
     * - Salt: prevents rainbow table attacks (must be unique per password)
     * - Iterations: computational cost (NIST recommends ≥ 600,000 for SHA-256)
     * - Output: 256-bit key suitable for AES or password hashing
     *
     * See also: Argon2, scrypt (memory-hard, more resistant to GPU attacks) in AdvancedCryptographyService
     */
    public static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLenBits)
        throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBits);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = skf.generateSecret(spec).getEncoded();
        spec.clearPassword(); // zero out password from memory
        return key;
    }

    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    // ── AES Key Wrap (encrypt a key with another key) ─────────────────────────

    public static byte[] aesWrapKey(SecretKey keyToWrap, SecretKey wrappingKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AESWrap");
        cipher.init(Cipher.WRAP_MODE, wrappingKey);
        return cipher.wrap(keyToWrap);
    }

    public static Key aesUnwrapKey(byte[] wrappedKey, SecretKey unwrappingKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AESWrap");
        cipher.init(Cipher.UNWRAP_MODE, unwrappingKey);
        return cipher.unwrap(wrappedKey, "AES", Cipher.SECRET_KEY);
    }

    // ── Avalanche effect demonstration ────────────────────────────────────────

    /**
     * Demonstrates SHA-256 avalanche effect:
     * one-bit change in input → ~128 bits (50%) different in output.
     */
    public static AvalancheResult demonstrateAvalanche(String input) throws NoSuchAlgorithmException {
        byte[] original = MessageDigest.getInstance("SHA-256")
            .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // Flip one bit in input
        byte[] modified = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        modified[0] ^= 0x01;
        byte[] flipped = MessageDigest.getInstance("SHA-256").digest(modified);

        int bitsChanged = 0;
        for (int i = 0; i < original.length; i++) {
            bitsChanged += Integer.bitCount(original[i] ^ flipped[i]);
        }

        return new AvalancheResult(bytesToHex(original), bytesToHex(flipped),
                                    bitsChanged, (double) bitsChanged / 256 * 100);
    }

    public record AvalancheResult(String originalHash, String modifiedHash,
                                   int bitsChanged, double percentChanged) {}

    // ── Amortized Analysis ────────────────────────────────────────────────────

    /**
     * Amortized Analysis demonstration using dynamic array (ArrayList-like) resizing.
     *
     * Actual cost of n insertions: O(1) for non-resize ops, O(n) for resize ops.
     * Resize happens at sizes 1, 2, 4, 8, ..., 2^k → cost 1+2+4+...+n = 2n total.
     * Amortized cost per insertion = 2n / n = O(1).
     *
     * Using "accounting method": each insertion pays 1 "credit" for itself + 1 for future resize.
     * Total prepaid credits always cover resize cost → O(1) amortized.
     */
    public static AmortizedAnalysis analyzeArrayResizing(int n) {
        int capacity = 1, actualCost = 0;
        int[] insertionCosts = new int[n];

        for (int i = 0; i < n; i++) {
            if (i == capacity) {
                actualCost += capacity; // copy all elements during resize
                capacity   *= 2;
            }
            actualCost++;
            insertionCosts[i] = 1 + (Integer.bitCount(i + 1) == 1 ? i : 0);
        }

        return new AmortizedAnalysis(n, actualCost, (double) actualCost / n,
                                      "O(1) amortized via accounting method");
    }

    public record AmortizedAnalysis(int operations, int totalCost,
                                     double amortizedCostPerOp, String method) {}

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] bytes = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
            bytes[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        return bytes;
    }
}
