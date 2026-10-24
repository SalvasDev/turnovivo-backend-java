package com.saldev.turnovivo.service;

import com.saldev.turnovivo.dto.request.UserRegisterRequest;
import com.saldev.turnovivo.dto.request.AdminUserCreateRequest;
import com.saldev.turnovivo.dto.response.UserResponse;
import com.saldev.turnovivo.exception.BusinessException;
import com.saldev.turnovivo.mapper.UserMapper;
import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
    }

    /*
     * Registers a new user into the platform.
     * Guaranted atomic operation via Spring's Transactional context.
    */
    @Transactional
    public UserResponse register(UserRegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        // 1. 0(1) Check for existing email to prevent unnecessary cryptoprocessing everhead
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException("El correo electrónico ya está registrado.", HttpStatus.CONFLICT);
        }

        request.setEmail(normalizedEmail);
        User user = userMapper.toEntity(request);
        user.setPasswordHash(passwordEncoder.encode(request.getPasswordHash()));
        return toResponse(userRepository.save(user));
    }

    @Transactional
    public UserResponse createAdmin(AdminUserCreateRequest request) {
        String normalizedEmail = normalizeEmail(request.email());
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new BusinessException("El correo electrónico ya está registrado.", HttpStatus.CONFLICT);
        }
        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.ADMIN)
                .build();
        return toResponse(userRepository.save(user));
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(Role role) {
        List<User> users = (role == null)
                ? userRepository.findAllByOrderByEmailAsc()
                : userRepository.findByRoleOrderByEmailAsc(role);
        return users.stream().map(this::toResponse).toList();
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }
}
