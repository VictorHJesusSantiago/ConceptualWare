package com.conceptualware.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class MFAService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int TOTP_DIGITS       = 6;
    private static final int WINDOW_STEPS      = 1;

    public static byte[] generateSecret() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    public static String secretToBase32(byte[] secret) {
        return base32Encode(secret);
    }

    public static int hotp(byte[] secret, long counter) throws Exception {
        byte[] msg  = ByteBuffer.allocate(8).putLong(counter).array();
        Mac    mac  = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(msg);

        int offset = hash[hash.length - 1] & 0x0F;
        int binCode = ((hash[offset]     & 0x7F) << 24) |
                      ((hash[offset + 1] & 0xFF) << 16) |
                      ((hash[offset + 2] & 0xFF) <<  8) |
                       (hash[offset + 3] & 0xFF);

        return binCode % (int) Math.pow(10, TOTP_DIGITS);
    }

    public static String generateTOTP(byte[] secret) throws Exception {
        long t = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        return String.format("%06d", hotp(secret, t));
    }

    public static String generateTOTP(byte[] secret, long unixTimestamp) throws Exception {
        long t = unixTimestamp / TIME_STEP_SECONDS;
        return String.format("%06d", hotp(secret, t));
    }

    public static boolean verifyTOTP(byte[] secret, String code) throws Exception {
        if (code == null || code.length() != TOTP_DIGITS) return false;
        int inputCode;
        try { inputCode = Integer.parseInt(code); }
        catch (NumberFormatException e) { return false; }

        long currentStep = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;

        for (int delta = -WINDOW_STEPS; delta <= WINDOW_STEPS; delta++) {
            if (hotp(secret, currentStep + delta) == inputCode) return true;
        }
        return false;
    }

    public static String generateOtpAuthUri(String issuer, String account, byte[] secret) {
        String encodedIssuer  = urlEncode(issuer);
        String encodedAccount = urlEncode(account);
        String base32Secret   = base32Encode(secret);

        return "otpauth://totp/" + encodedIssuer + ":" + encodedAccount
             + "?secret=" + base32Secret
             + "&issuer=" + encodedIssuer
             + "&algorithm=SHA1"
             + "&digits=" + TOTP_DIGITS
             + "&period=" + TIME_STEP_SECONDS;
    }

    public static List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom rng   = new SecureRandom();
        String alphabet    = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        for (int i = 0; i < 10; i++) {
            StringBuilder code = new StringBuilder();
            for (int j = 0; j < 10; j++)
                code.append(alphabet.charAt(rng.nextInt(alphabet.length())));
            codes.add(code.toString());
        }
        return codes;
    }

    public record MFAEnrollment(
        String userId,
        byte[] secret,
        String base32Secret,
        String otpAuthUri,
        List<String> backupCodes,
        long   enrolledAt
    ) {
        public static MFAEnrollment enroll(String userId, String issuer) {
            byte[] secret = generateSecret();
            return new MFAEnrollment(
                userId, secret, base32Encode(secret),
                generateOtpAuthUri(issuer, userId, secret),
                generateBackupCodes(),
                Instant.now().getEpochSecond()
            );
        }
    }

    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32_CHARS.charAt((buffer >> (bitsLeft - 5)) & 31));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) sb.append(BASE32_CHARS.charAt((buffer << (5 - bitsLeft)) & 31));
        while (sb.length() % 8 != 0) sb.append('=');
        return sb.toString();
    }

    private static String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }
        catch (java.io.UnsupportedEncodingException e) { return s; }
    }

    public record MFAPolicy(
        boolean required,
        boolean allowRemember,
        int     rememberDays,
        Set<String> exemptRoles
    ) {
        public static MFAPolicy highSecurity() {
            return new MFAPolicy(true, false, 0, Set.of("service-account"));
        }
        public static MFAPolicy standard() {
            return new MFAPolicy(true, true, 30, Set.of("service-account", "readonly"));
        }
    }
}
