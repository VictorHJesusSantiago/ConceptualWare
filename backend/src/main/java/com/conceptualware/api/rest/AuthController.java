package com.conceptualware.api.rest;

import com.conceptualware.domain.user.User;
import com.conceptualware.infrastructure.persistence.UserRepository;
import com.conceptualware.infrastructure.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;

/**
 * Concept #21 — Segurança: Autenticação, JWT, Refresh Token, BCrypt,
 *   Política de senhas, Gestão de sessões
 * Concept #25 — REST: Resource, POST, Request Body, Status Codes 201/401/409
 * Concept #14 — Clean Code: Guard clauses, early return
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    // ── POST /api/v1/auth/register ────────────────────────────────────────────

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        // Sanitize input — Concept #21
        if (userRepository.existsByEmail(request.email()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        if (userRepository.existsByUsername(request.username()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already taken");

        // BCrypt password hashing — Concept #21
        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.email(), request.username(), passwordHash);
        userRepository.save(user);

        log.info("User registered: username={}", request.username());
        return generateTokens(user);
    }

    // ── POST /api/v1/auth/login ───────────────────────────────────────────────

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        // Constant-time comparison (prevent timing attacks) — Concept #21
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");

        if (!user.isActive())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account not active");

        user.recordActivity();
        userRepository.save(user);

        log.info("User logged in: id={}", user.getId());
        return generateTokens(user);
    }

    // ── POST /api/v1/auth/refresh ─────────────────────────────────────────────

    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || !jwtService.validateToken(refreshToken)
                || !jwtService.isRefreshToken(refreshToken))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");

        String userId = jwtService.extractUserId(refreshToken);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        if (!user.isRefreshTokenValid(refreshToken))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token revoked");

        user.revokeRefreshToken(refreshToken);
        userRepository.save(user);
        return generateTokens(user);
    }

    // ── POST /api/v1/auth/logout ──────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) return ResponseEntity.badRequest().build();

        jwtService.extractUserId(refreshToken);
        // Token revocation would go here
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse generateTokens(User user) {
        Set<String> roles = new java.util.HashSet<>();
        user.getRoles().forEach(r -> roles.add(r.name()));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        user.storeRefreshToken(refreshToken);
        userRepository.save(user);

        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername());
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String username,
        @NotBlank @Size(min = 8, max = 128) String password
    ) {}

    public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    public record AuthResponse(
        String accessToken,
        String refreshToken,
        String userId,
        String username
    ) {}
}
