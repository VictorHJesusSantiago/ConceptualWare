package com.conceptualware.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Concept #21 — Multi-Factor Authentication (MFA / 2FA):
 *
 *   TOTP (Time-based One-Time Password) — RFC 6238:
 *     Based on HOTP (HMAC-based OTP, RFC 4226) but uses time as counter.
 *     Algorithm:
 *       1. T = floor(unix_timestamp / 30)  — 30-second time step
 *       2. HMAC-SHA1(secret, T)            — 20-byte MAC
 *       3. Dynamic Truncation → 31-bit integer
 *       4. TOTP = integer mod 10^6         — 6-digit code
 *
 *     Compatible with: Google Authenticator, Authy, Microsoft Authenticator,
 *                      1Password, Bitwarden, FreeOTP.
 *
 *   HOTP (Counter-based OTP) — RFC 4226:
 *     Same as TOTP but uses a synchronized counter instead of time.
 *     Stateful: requires counter sync between client and server.
 *
 *   Backup codes: one-time use codes for account recovery.
 *     Must be: random, 8-10 chars, bcrypt-hashed in storage.
 *
 *   MFA enrollment flow:
 *     1. Server generates secret (16+ bytes, base32-encoded).
 *     2. Server sends otpauth:// URI to client.
 *     3. Client scans QR code with authenticator app.
 *     4. Client sends first TOTP code to confirm setup.
 *     5. Server verifies and stores the secret for future logins.
 *
 * Concept #21 — Authentication factors: something you know + something you have
 */
@Service
public class MFAService {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int TOTP_DIGITS       = 6;
    private static final int WINDOW_STEPS      = 1; // accept codes ±1 time step (clock skew)

    // ── Secret generation ─────────────────────────────────────────────────────

    /** Generate a random TOTP secret (160 bits = 20 bytes, base32-encoded). */
    public static byte[] generateSecret() {
        byte[] secret = new byte[20];
        new SecureRandom().nextBytes(secret);
        return secret;
    }

    public static String secretToBase32(byte[] secret) {
        return base32Encode(secret);
    }

    // ── HOTP (RFC 4226) ───────────────────────────────────────────────────────

    /**
     * Compute HOTP for a given counter value.
     * This is the core algorithm used by both HOTP and TOTP.
     */
    public static int hotp(byte[] secret, long counter) throws Exception {
        // Step 1: HMAC-SHA1 of the 8-byte big-endian counter
        byte[] msg  = ByteBuffer.allocate(8).putLong(counter).array();
        Mac    mac  = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] hash = mac.doFinal(msg);

        // Step 2: Dynamic Truncation — take 4 bytes starting at the offset byte
        int offset = hash[hash.length - 1] & 0x0F;
        int binCode = ((hash[offset]     & 0x7F) << 24) |
                      ((hash[offset + 1] & 0xFF) << 16) |
                      ((hash[offset + 2] & 0xFF) <<  8) |
                       (hash[offset + 3] & 0xFF);

        // Step 3: Truncate to TOTP_DIGITS digits
        return binCode % (int) Math.pow(10, TOTP_DIGITS);
    }

    // ── TOTP (RFC 6238) ───────────────────────────────────────────────────────

    /** Generate TOTP code for the current time. */
    public static String generateTOTP(byte[] secret) throws Exception {
        long t = Instant.now().getEpochSecond() / TIME_STEP_SECONDS;
        return String.format("%06d", hotp(secret, t));
    }

    /** Generate TOTP code for a specific timestamp (for testing). */
    public static String generateTOTP(byte[] secret, long unixTimestamp) throws Exception {
        long t = unixTimestamp / TIME_STEP_SECONDS;
        return String.format("%06d", hotp(secret, t));
    }

    /**
     * Verify a TOTP code with a ±WINDOW_STEPS time window to tolerate clock skew.
     * Returns the matched time step offset (0 = current, ±1 = adjacent step).
     */
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

    // ── OTP Auth URI (for QR code) ────────────────────────────────────────────

    /**
     * Generate otpauth:// URI for QR code enrollment.
     * Scanning this URI with Google Authenticator adds the account.
     *
     * Format: otpauth://totp/issuer:account?secret=BASE32SECRET&issuer=issuer&algorithm=SHA1&digits=6&period=30
     */
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

    // ── Backup Codes ──────────────────────────────────────────────────────────

    /** Generate 10 single-use backup codes (each 10 random characters). */
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

    // ── MFA enrollment record ─────────────────────────────────────────────────

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

    // ── Base32 encoding (RFC 4648) ────────────────────────────────────────────

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

    // ── MFA policy ────────────────────────────────────────────────────────────

    public record MFAPolicy(
        boolean required,         // is MFA required for all users?
        boolean allowRemember,    // allow "remember this device for 30 days"?
        int     rememberDays,
        Set<String> exemptRoles  // roles exempt from MFA (e.g., service accounts)
    ) {
        public static MFAPolicy highSecurity() {
            return new MFAPolicy(true, false, 0, Set.of("service-account"));
        }
        public static MFAPolicy standard() {
            return new MFAPolicy(true, true, 30, Set.of("service-account", "readonly"));
        }
    }
}
