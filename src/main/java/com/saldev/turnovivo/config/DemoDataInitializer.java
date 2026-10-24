package com.saldev.turnovivo.config;

import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.UserRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DemoDataInitializer {

    private final DemoDataProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(
            DemoDataProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @Transactional
    public void initializeUsers() {
        createIfConfigured(properties.getSuperAdmin(), Role.SUPER_ADMIN);
        createIfMissing(properties.getAdmin(), Role.ADMIN);
        createIfMissing(properties.getStaff(), Role.STAFF);
        createIfMissing(properties.getCustomer(), Role.CUSTOMER);
    }

    private void createIfConfigured(DemoDataProperties.Credentials credentials, Role role) {
        if (credentials.getEmail() == null || credentials.getEmail().isBlank()
                || credentials.getPassword() == null || credentials.getPassword().isBlank()) {
            return;
        }
        createIfMissing(credentials, role);
    }

    private void createIfMissing(DemoDataProperties.Credentials credentials, Role role) {
        String normalizedEmail = credentials.getEmail().trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            return;
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(credentials.getPassword()))
                .role(role)
                .build();

        userRepository.save(user);
    }
}
