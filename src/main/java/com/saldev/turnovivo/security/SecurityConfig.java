package com.saldev.turnovivo.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // Enables method-level protection with @PreAuthorize.
public class SecurityConfig {
    private final JwtFilter jwtFilter;
    private final SecurityErrorWriter securityErrorWriter;

    // Inject the JWT inspector through the primary constructor.
    public SecurityConfig(JwtFilter jwtFilter, SecurityErrorWriter securityErrorWriter) {
        this.jwtFilter = jwtFilter;
        this.securityErrorWriter = securityErrorWriter;
    }

    /**
     * Defines the central Security Filter Chain.
     * HTTP requests are intercepted here at the application boundary.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF because REST APIs based on JWT bearer tokens do not use session cookies.
                .csrf(csrf -> csrf.disable())

                // 2. Set a stateless session policy.
                // Tomcat does not retain per-user session data in memory; every request is independent
                // and must carry its authentication token.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 3. Request authorization rules.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/").permitAll()

                        // Public authentication and user-registration routes.
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/users").permitAll()

                        // Public business and slot query routes available to anonymous visitors.
                        .requestMatchers(HttpMethod.GET, "/businesses/{slug}").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/appointments/business/{businessId}/available"
                        ).permitAll()

                        // Every other request requires a valid JWT.
                        .anyRequest().authenticated()
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) ->
                                securityErrorWriter.write(
                                        response,
                                        HttpStatus.UNAUTHORIZED,
                                        "Debes iniciar sesión para realizar esta acción."
                                ))
                        .accessDeniedHandler((request, response, exception) ->
                                securityErrorWriter.write(
                                        response,
                                        HttpStatus.FORBIDDEN,
                                        "No tienes autorización para realizar esta acción."
                                )))

                // Place JwtFilter immediately before UsernamePasswordAuthenticationFilter so it
                // resolves the token and authenticated user before downstream authorization.
                    .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Defines the application's password-encoding component.
     * Uses the secure industry-standard BCrypt algorithm.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // Default hashing strength is 10.
        return new BCryptPasswordEncoder(10);
    }
}
