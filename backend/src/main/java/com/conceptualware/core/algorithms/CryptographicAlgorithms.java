package com.conceptualware.core.algorithms;

import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

public class CryptographicAlgorithms {

    public static final int AES_KEY_BITS = 256;
    public static final int GCM_IV_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;

    public static SecretKey generateAESKey() throws NoSuchAlgorithmException {
        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(AES_KEY_BITS, new SecureRandom());
        return kg.generateKey();
    }

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

    public static KeyPair generateRSAKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048, new SecureRandom());
        return kpg.generateKeyPair();
    }

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

    public static String sha256Hex(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    public static byte[] sha256Bytes(byte[] input) throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    public static String sha512Hex(String input) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-512")
            .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    public static String hmacSha256(String message, byte[] key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return bytesToHex(mac.doFinal(message.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    public static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLenBits)
        throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyLenBits);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] key = skf.generateSecret(spec).getEncoded();
        spec.clearPassword();
        return key;
    }

    public static byte[] generateSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

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

    public static AvalancheResult demonstrateAvalanche(String input) throws NoSuchAlgorithmException {
        byte[] original = MessageDigest.getInstance("SHA-256")
            .digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        byte[] modified = input.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        modified[0] ^= 0x01;
        byte[] flipped = MessageDigest.getInstance("SHA-256").digest(modified);

        int bitsChanged = 0;
        for (int i = 0; i < original.length; i++) {
            bitsChanged += Integer.bitCount((original[i] & 0xFF) ^ (flipped[i] & 0xFF));
        }

        return new AvalancheResult(bytesToHex(original), bytesToHex(flipped),
                                    bitsChanged, (double) bitsChanged / 256 * 100);
    }

    public record AvalancheResult(String originalHash, String modifiedHash,
                                   int bitsChanged, double percentChanged) {}

    public static AmortizedAnalysis analyzeArrayResizing(int n) {
        int capacity = 1, actualCost = 0;
        int[] insertionCosts = new int[n];

        for (int i = 0; i < n; i++) {
            if (i == capacity) {
                actualCost += capacity;
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
