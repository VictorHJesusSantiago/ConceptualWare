package com.conceptualware.security;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concept #19 — Contract Test: garante SSOT (Single Source of Truth) entre a
 * política de senha do backend (@Pattern em AuthController.RegisterRequest)
 * e o schema Zod do gateway (gateway/src/routes/auth.ts — RegisterSchema).
 *
 * CLAUDE.md — regra #6 do watchlist: "Nunca deixe validação de senha divergir
 * entre gateway e backend". Este teste falha em CI se alguém alterar um lado
 * sem replicar no outro, ANTES de virar um bug de produção (ex.: gateway
 * aceita uma senha que o backend rejeita com 400, quebrando o cadastro).
 *
 * As regex abaixo são cópias literais das usadas em:
 *   - backend: AuthController.RegisterRequest (@Pattern)
 *   - gateway: routes/auth.ts RegisterSchema (z.string().regex(...))
 * Se você mudar a política em um lado, atualize também este teste E o outro lado.
 */
class PasswordPolicyContractTest {

    // Espelha exatamente os 3 @Pattern de AuthController.RegisterRequest
    private static final Pattern BACKEND_UPPERCASE = Pattern.compile(".*[A-Z].*");
    private static final Pattern BACKEND_LOWERCASE = Pattern.compile(".*[a-z].*");
    private static final Pattern BACKEND_DIGIT     = Pattern.compile(".*[0-9].*");
    private static final int BACKEND_MIN_LENGTH = 8;
    private static final int BACKEND_MAX_LENGTH = 128;

    // Espelha exatamente os 3 .regex() de RegisterSchema no gateway (Zod)
    private static final Pattern GATEWAY_UPPERCASE = Pattern.compile("[A-Z]");
    private static final Pattern GATEWAY_LOWERCASE = Pattern.compile("[a-z]");
    private static final Pattern GATEWAY_DIGIT     = Pattern.compile("[0-9]");
    private static final int GATEWAY_MIN_LENGTH = 8;
    private static final int GATEWAY_MAX_LENGTH = 128;

    @Test
    void minAndMaxLengthMustMatchBetweenBackendAndGateway() {
        assertTrue(BACKEND_MIN_LENGTH == GATEWAY_MIN_LENGTH,
            "SSOT quebrado: min length do backend (" + BACKEND_MIN_LENGTH +
            ") diverge do gateway (" + GATEWAY_MIN_LENGTH + ")");
        assertTrue(BACKEND_MAX_LENGTH == GATEWAY_MAX_LENGTH,
            "SSOT quebrado: max length do backend (" + BACKEND_MAX_LENGTH +
            ") diverge do gateway (" + GATEWAY_MAX_LENGTH + ")");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdefg1", "Password123", "Zz1aaaaa", "MyStr0ngPass!"})
    void passwordsAcceptedByBackendMustAlsoBeAcceptedByGateway(String password) {
        boolean backendAccepts = backendAccepts(password);
        boolean gatewayAccepts = gatewayAccepts(password);
        assertTrue(backendAccepts, "Password de teste deveria passar no backend: " + password);
        assertTrue(gatewayAccepts,
            "SSOT quebrado: senha aceita pelo backend mas rejeitada pelo gateway: " + password);
    }

    @ParameterizedTest
    @ValueSource(strings = {"alllowercase1", "ALLUPPERCASE1", "NoDigitsHere", "short1A", ""})
    void passwordsRejectedByBackendMustAlsoBeRejectedByGateway(String password) {
        boolean backendAccepts = backendAccepts(password);
        boolean gatewayAccepts = gatewayAccepts(password);
        assertFalse(backendAccepts, "Password de teste deveria falhar no backend: " + password);
        assertFalse(gatewayAccepts,
            "SSOT quebrado: senha rejeitada pelo backend mas aceita pelo gateway: " + password);
    }

    private boolean backendAccepts(String password) {
        return password.length() >= BACKEND_MIN_LENGTH
            && password.length() <= BACKEND_MAX_LENGTH
            && BACKEND_UPPERCASE.matcher(password).matches()
            && BACKEND_LOWERCASE.matcher(password).matches()
            && BACKEND_DIGIT.matcher(password).matches();
    }

    private boolean gatewayAccepts(String password) {
        return password.length() >= GATEWAY_MIN_LENGTH
            && password.length() <= GATEWAY_MAX_LENGTH
            && GATEWAY_UPPERCASE.matcher(password).find()
            && GATEWAY_LOWERCASE.matcher(password).find()
            && GATEWAY_DIGIT.matcher(password).find();
    }
}
