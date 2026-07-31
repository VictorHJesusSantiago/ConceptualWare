package com.conceptualware.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class CsrfTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long TOKEN_VALIDITY_MS = 30 * 60 * 1000L;
    private static final int MIN_SECRET_LENGTH = 32;

    private final byte[] secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CsrfTokenService(
            @org.springframework.beans.factory.annotation.Value("${app.csrf.secret:}") String configuredSecret,
            org.springframework.core.env.Environment environment) {

        boolean isProd = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (configuredSecret == null || configuredSecret.isBlank()) {
            if (isProd) {
                throw new IllegalStateException(
                    "app.csrf.secret (env CSRF_SECRET) é obrigatório no perfil 'prod'. "
                        + "Gere com: openssl rand -base64 48");
            }
            this.secretKey = new byte[MIN_SECRET_LENGTH];
            secureRandom.nextBytes(this.secretKey);
            org.slf4j.LoggerFactory.getLogger(CsrfTokenService.class).warn(
                "CSRF_SECRET não configurado — usando segredo efêmero por instância. "
                    + "Aceitável apenas fora de produção e com uma única réplica.");
            return;
        }

        byte[] secretBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "app.csrf.secret deve ter ao menos " + MIN_SECRET_LENGTH + " bytes (256 bits); "
                    + "recebido " + secretBytes.length + ".");
        }
        this.secretKey = secretBytes;
    }

    public String generateToken() {
        byte[] nonce = new byte[16];
        secureRandom.nextBytes(nonce);
        String nonceB64 = Base64.getUrlEncoder().withoutPadding().encodeToString(nonce);
        long timestamp = System.currentTimeMillis();
        String payload = nonceB64 + "." + timestamp;
        String signature = hmac(payload);
        return payload + "." + signature;
    }

    public boolean isValid(String token) {
        if (token == null) return false;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return false;

        String payload = parts[0] + "." + parts[1];
        String signature = parts[2];

        if (!constantTimeEquals(hmac(payload), signature)) return false;

        try {
            long timestamp = Long.parseLong(parts[1]);
            return (System.currentTimeMillis() - timestamp) <= TOKEN_VALIDITY_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secretKey, HMAC_ALGO));
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao computar HMAC do token CSRF", e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
