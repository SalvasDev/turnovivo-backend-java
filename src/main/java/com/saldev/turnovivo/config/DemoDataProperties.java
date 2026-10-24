package com.saldev.turnovivo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Profile("dev")
@Validated
@ConfigurationProperties(prefix = "app.demo")
public class DemoDataProperties {

    private final Credentials superAdmin = new Credentials();

    @Valid
    private final Credentials admin = new Credentials();

    @Valid
    private final Credentials staff = new Credentials();

    @Valid
    private final Credentials customer = new Credentials();

    public Credentials getAdmin() {
        return admin;
    }

    public Credentials getSuperAdmin() {
        return superAdmin;
    }

    public Credentials getStaff() {
        return staff;
    }

    public Credentials getCustomer() {
        return customer;
    }

    public static class Credentials {

        @NotBlank
        @Email
        private String email;

        @NotBlank
        @Size(min = 6)
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
