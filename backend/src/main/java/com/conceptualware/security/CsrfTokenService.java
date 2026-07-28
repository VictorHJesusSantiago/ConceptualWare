package com.conceptualware.security;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Concept #21 — CSRF Protection: padrão "Double-Submit Cookie" assinado com HMAC.
 *
 *   Como funciona:
 *   1. Servidor gera um token aleatório + timestamp e o assina com HMAC-SHA256
 *      usando um segredo que só o servidor conhece (evita forjar tokens).
 *   2. Token é enviado ao cliente em um cookie NÃO HttpOnly (JS precisa lê-lo)
 *      e o cliente deve reenviá-lo em um header custom (ex.: X-CSRF-Token) em
 *      toda requisição que muda estado (POST/PUT/PATCH/DELETE).
 *   3. Um atacante em outro domínio pode fazer o browser enviar o cookie
 *      automaticamente, mas NÃO consegue ler seu valor para colocá-lo no header
 *      (Same-Origin Policy) — por isso "double submit" neutraliza CSRF.
 *
 *   Nota: esta API usa JWT em memória/sessionStorage (ADR-004), não cookies de
 *   sessão HttpOnly — por isso CSRF clássico tem risco reduzido aqui. Este
 *   serviço existe como demonstração do conceito e para proteger o cenário
 *   futuro (fase 2 do ADR-004: refresh token em cookie HttpOnly).
 */
@Service
public class CsrfTokenService {

    private static final String HMAC_ALGO = "HmacSHA256";
    private static final long TOKEN_VALIDITY_MS = 30 * 60 * 1000L; // 30 minutos
    private final byte[] secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public CsrfTokenService() {
        // Em produção: injetar via CSRF_SECRET (env var), nunca hardcoded.
        // Aqui geramos por instância (suficiente para demo single-node).
        this.secretKey = new byte[32];
        secureRandom.nextBytes(secretKey);
    }

    /** Gera token no formato: base64(nonce).timestamp.hmacHex */
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

        // Comparação em tempo constante — evita timing attack na validação da assinatura
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
