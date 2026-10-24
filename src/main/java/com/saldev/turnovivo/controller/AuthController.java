package com.saldev.turnovivo.controller;

import com.saldev.turnovivo.dto.request.LoginRequest;
import com.saldev.turnovivo.dto.response.AuthResponse;
import com.saldev.turnovivo.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /*
     * Authenticates a user and establishes a session.
     * Uses @Valid to intercept invalid JSON properties on the perimeter.
     */
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        // Delegate directly to the business layer.
        return authService.login(request);
    }

    /*
     * Revokes the user session from the Redis cache storage.
     * @AuthenticationPrincipal extracts the active token subject without DB queries.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal String userId) {
        authService.logout(userId);
        // Return a successful 204 response without a body.
        return ResponseEntity.noContent().build();
    }
}
