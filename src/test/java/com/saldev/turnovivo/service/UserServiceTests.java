package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.request.AdminUserCreateRequest;
import com.saldev.turnovivo.dto.request.UserRegisterRequest;
import com.saldev.turnovivo.dto.response.UserResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.mapper.UserMapper;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTests {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, passwordEncoder, new UserMapper());
    }

    private void stubUserPersistence() {
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
    }

    @Test
    void publicRegistrationAlwaysCreatesCustomerAndReturnsSafeContract() {
        stubUserPersistence();
        UserRegisterRequest request = new UserRegisterRequest(
                "  CUSTOMER@EXAMPLE.COM ",
                "secret123",
                "SUPER_ADMIN"
        );
        when(userRepository.findByEmail("customer@example.com")).thenReturn(Optional.empty());

        UserResponse response = service.register(request);

        assertThat(response.email()).isEqualTo("customer@example.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void administrativeCreationAlwaysCreatesAdmin() {
        stubUserPersistence();
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());

        UserResponse response = service.createAdmin(
                new AdminUserCreateRequest("OWNER@EXAMPLE.COM", "secret123")
        );

        assertThat(response.email()).isEqualTo("owner@example.com");
        assertThat(response.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void administrativeCreationRejectsAnExistingEmailBeforeEncodingOrSaving() {
        User existingUser = User.builder()
                .id(UUID.randomUUID())
                .email("owner@example.com")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByEmail("owner@example.com")).thenReturn(Optional.of(existingUser));

        assertThatThrownBy(() -> service.createAdmin(
                new AdminUserCreateRequest(" OWNER@EXAMPLE.COM ", "secret123")
        ))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus())
                        .isEqualTo(HttpStatus.CONFLICT))
                .hasMessage("El correo electrónico ya está registrado.");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void listUsersFiltersByRoleAndReturnsSafeContracts() {
        UUID adminId = UUID.randomUUID();
        User admin = User.builder()
                .id(adminId)
                .email("owner@example.com")
                .passwordHash("encoded-secret")
                .role(Role.ADMIN)
                .build();
        when(userRepository.findByRoleOrderByEmailAsc(Role.ADMIN)).thenReturn(List.of(admin));

        List<UserResponse> response = service.listUsers(Role.ADMIN);

        assertThat(response).containsExactly(
                new UserResponse(adminId, "owner@example.com", Role.ADMIN)
        );
        verify(userRepository).findByRoleOrderByEmailAsc(Role.ADMIN);
    }

    @Test
    void listUsersWithoutRoleReturnsAllUsersInRepositoryOrder() {
        User firstUser = User.builder()
                .id(UUID.randomUUID())
                .email("admin@example.com")
                .role(Role.ADMIN)
                .build();
        User secondUser = User.builder()
                .id(UUID.randomUUID())
                .email("customer@example.com")
                .role(Role.CUSTOMER)
                .build();
        when(userRepository.findAllByOrderByEmailAsc())
                .thenReturn(List.of(firstUser, secondUser));

        List<UserResponse> response = service.listUsers(null);

        assertThat(response).containsExactly(
                new UserResponse(firstUser.getId(), firstUser.getEmail(), firstUser.getRole()),
                new UserResponse(secondUser.getId(), secondUser.getEmail(), secondUser.getRole())
        );
        verify(userRepository).findAllByOrderByEmailAsc();
        verify(userRepository, never()).findByRoleOrderByEmailAsc(any());
    }
}
