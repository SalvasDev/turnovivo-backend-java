package com.saldev.turnovivo.config;

import com.saldev.turnovivo.model.entity.User;
import com.saldev.turnovivo.model.enums.Role;
import com.saldev.turnovivo.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/*
 * Seeds an initial SUPER_ADMIN user in production, in an idempotent manner.
 *
 * This component resolves the "chicken and egg" problem: the endpoint
 * for creating administrators ({@code POST /admin/users}) requires an
 * authenticated SUPER_ADMIN, meaning that in a fresh database, no one
 * would be able to initiate the chain of trust. This initializer creates
 * that first SUPER_ADMIN using environment variables
 * (SEED_SUPER_ADMIN_EMAIL / SEED_SUPER_ADMIN_PASSWORD).
 *
 * Security rules:
 * - If no credentials are configured, nothing is created (standard startup).
 * - If the user already exists, it remains unchanged (idempotent).
 * - The password must be at least 6 characters long.
 */
@Component
@Profile("prod")
public class PlatformSeedInitializer {

    private static final Logger logger = LoggerFactory.getLogger(PlatformSeedInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final PlatformSeedProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public PlatformSeedInitializer(
            PlatformSeedProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void seedSuperAdmin() {
        try {
            doSeedSuperAdmin();
        } catch (RuntimeException ex) {
            // A failure during seeding must NEVER bring down the application in production.
            logger.error("No se pudo sembrar el SUPER_ADMIN inicial; la aplicación continúa su arranque.", ex);
        }
    }

    protected void doSeedSuperAdmin() {
        PlatformSeedProperties.SuperAdmin superAdmin = properties.getSuperAdmin();
        String email = superAdmin.getEmail();
        String password = superAdmin.getPassword();

        if (isBlank(email) || isBlank(password)) {
            logger.info("No se definió SEED_SUPER_ADMIN_EMAIL/PASSWORD; no se sembró ningún SUPER_ADMIN inicial.");
            return;
        }

        if (password.length() < MIN_PASSWORD_LENGTH) {
            logger.error("SEED_SUPER_ADMIN_PASSWORD es demasiado corta (mínimo {} caracteres); no se creó el SUPER_ADMIN.",
                    MIN_PASSWORD_LENGTH);
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.findByEmail(normalizedEmail).isPresent()) {
            logger.info("El SUPER_ADMIN inicial ya existe ({}); nada que sembrar.", normalizedEmail);
            return;
        }

        User superAdminUser = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(password))
                .role(Role.SUPER_ADMIN)
                .build();

        userRepository.save(superAdminUser);
        logger.info("SUPER_ADMIN inicial creado correctamente: {}", normalizedEmail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
