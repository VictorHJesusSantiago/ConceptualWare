package com.conceptualware.api.rest;

import com.conceptualware.domain.user.User;
import com.conceptualware.infrastructure.persistence.UserRepository;
import com.conceptualware.infrastructure.security.JwtService;
import com.conceptualware.security.SecurityAuditService;
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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService securityAuditService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        boolean emailExists    = userRepository.existsByEmail(request.email());
        boolean usernameExists = userRepository.existsByUsername(request.username());
        if (emailExists || usernameExists)
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Registration failed — credentials already in use");

        String passwordHash = passwordEncoder.encode(request.password());
        User user = User.create(request.email(), request.username(), passwordHash);
        userRepository.save(user);

        log.info("User registered: username={}", request.username());
        return generateTokens(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.isLockedOut()) {
            long secondsRemaining = user.lockoutRemaining().getSeconds();
            securityAuditService.record(SecurityAuditService.EventType.ACCOUNT_LOCKOUT,
                user.getId(), "Login attempt while locked out, " + secondsRemaining + "s remaining");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Account temporarily locked. Try again in " + secondsRemaining + " seconds.");
        }

        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            user.recordFailedLogin();
            userRepository.save(user);
            securityAuditService.record(SecurityAuditService.EventType.LOGIN_FAILURE,
                user.getId(), "Invalid password");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        user.resetFailedLogins();
        user.recordActivity();
        userRepository.save(user);

        log.info("User logged in: id={}", user.getId());
        return generateTokens(user);
    }

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

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null) return ResponseEntity.badRequest().build();

        if (jwtService.validateToken(refreshToken) && jwtService.isRefreshToken(refreshToken)) {
            String userId = jwtService.extractUserId(refreshToken);
            userRepository.findById(userId).ifPresent(user -> {
                user.revokeRefreshToken(refreshToken);
                userRepository.save(user);
            });
        }
        return ResponseEntity.noContent().build();
    }

    private AuthResponse generateTokens(User user) {
        Set<String> roles = new java.util.HashSet<>();
        user.getRoles().forEach(r -> roles.add(r.name()));

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        user.storeRefreshToken(refreshToken);
        userRepository.save(user);

        return new AuthResponse(accessToken, refreshToken, user.getId(), user.getUsername());
    }

    public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-zA-Z0-9_-]+$") String username,
        @NotBlank
        @Size(min = 8, max = 128)
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain at least one uppercase letter")
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain at least one lowercase letter")
        @Pattern(regexp = ".*[0-9].*", message = "Password must contain at least one digit")
        String password
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
