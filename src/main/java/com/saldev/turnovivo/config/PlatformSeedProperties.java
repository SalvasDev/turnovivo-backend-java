package com.saldev.turnovivo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/*
 * Optional credentials for seeding an initial SUPER_ADMIN user in the
 * production profile.
 *
 * Unlike {@link DemoDataProperties} (the "dev" profile), these properties
 * are neither mandatory nor strictly validated: if they are not defined, the
 * application startup proceeds without creating any user. This prevents
 * deployment from being blocked and allows for the secure seeding of the
 * platform's first administrator via environment variables, without
 * exposing secrets in the repository.
 */
@Component
@Profile("prod")
@ConfigurationProperties(prefix = "app.seed")
public class PlatformSeedProperties {

    private final SuperAdmin superAdmin = new SuperAdmin();

    public SuperAdmin getSuperAdmin() {
        return superAdmin;
    }

    public static class SuperAdmin {

        private String email;
        private String password;

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
