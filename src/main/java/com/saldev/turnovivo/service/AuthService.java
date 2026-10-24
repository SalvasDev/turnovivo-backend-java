package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.request.LoginRequest;
import com.saldev.turnovivo.dto.response.AuthResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.repository.UserRepository;
import com.saldev.turnovivo.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SessionService sessionService;
    private static final String INVALID_CREDENTIALS_MSG = "Invalid email or pasword.";

    // Constructor injection keeps dependencies explicit and decoupled.
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider,
                       SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.sessionService = sessionService;
    }

    /*
    * Synchronized with the original NestJS logic: "login(loginDto)".
    * Performs cryptographic validation and stores the immutable token in Redis.
    */
    public AuthResponse login(LoginRequest request) {
        // 1. Find the user in the PostgreSQL database.
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessException(INVALID_CREDENTIALS_MSG, HttpStatus.UNAUTHORIZED));

        // 2. Securely compare password hashes using BCrypt.
        if (!passwordEncoder.matches(request.getPasswordHash(), user.getPasswordHash())) {
            throw new BusinessException(INVALID_CREDENTIALS_MSG, HttpStatus.UNAUTHORIZED);
        }

        // 3. Generate the token with the ID, email, and role in the JWT claims.
        String accessToken = jwtProvider.generateToken(
                user.getId().toString(),
                user.getEmail(),
                user.getRole().name()
        );

        // 4. Store the active session in Redis as plain text.
        // Expiration is configured for 24 hours (86,400 seconds), matching the original NestJS implementation.
        sessionService.activate(user.getId().toString());

        return new AuthResponse(accessToken);
    }

    /*
    * Synchronized with the original logic: logout(userId).
    * Removes the Redis session state immediately in O(1).
    */
    public void logout(String userId) {
        sessionService.revoke(userId);
    }

}
