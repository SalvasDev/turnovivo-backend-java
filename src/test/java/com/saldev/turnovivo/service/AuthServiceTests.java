package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.request.LoginRequest;
import com.saldev.turnovivo.dto.response.AuthResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.UserRepository;
import com.saldev.turnovivo.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private SessionService sessionService;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, passwordEncoder, jwtProvider, sessionService);
    }

    @Test
    void validCredentialsGenerateTokenAndActivateSession() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("customer@example.com")
                .passwordHash("stored-password-hash")
                .role(Role.CUSTOMER)
                .build();
        LoginRequest request = new LoginRequest("customer@example.com", "secret123");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "stored-password-hash")).thenReturn(true);
        when(jwtProvider.generateToken(
                userId.toString(),
                "customer@example.com",
                Role.CUSTOMER.name()
        )).thenReturn("signed-jwt");

        AuthResponse response = service.login(request);

        assertThat(response.getAccessToken()).isEqualTo("signed-jwt");
        verify(sessionService).activate(userId.toString());
    }

    @Test
    void invalidPasswordDoesNotGenerateTokenOrActivateSession() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .passwordHash("stored-password-hash")
                .role(Role.CUSTOMER)
                .build();
        LoginRequest request = new LoginRequest("customer@example.com", "wrong-password");
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "stored-password-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus())
                        .isEqualTo(HttpStatus.UNAUTHORIZED))
                .hasMessage("Invalid email or pasword.");

        verify(jwtProvider, never()).generateToken(anyString(), anyString(), anyString());
        verify(sessionService, never()).activate(anyString());
    }
}
