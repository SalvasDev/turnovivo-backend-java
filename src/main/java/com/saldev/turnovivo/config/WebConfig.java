package com.saldev.turnovivo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Comma-separated list of authorized frontend origins.
     *
     * Supports patterns through {@code allowedOriginPatterns}, allowing Vercel
     * preview deployments to be authorized with wildcards, for example:
     * "https://turnovivo.vercel.app,https://*.vercel.app".
     *
     * Resolution order: APP_FRONTEND_URLS (prod) → app.frontend.urls →
     * APP_FRONTEND_URL (compatibility) → app.frontend-url → localhost dev.
     */
    @Value("${APP_FRONTEND_URLS:${app.frontend.urls:${APP_FRONTEND_URL:${app.frontend-url:http://localhost:3001}}}}")
    private String frontendUrls;

    /**
     * Applies CORS handshake rules for browser requests.
     * Explicitly defines trusted origins and the actions they may perform.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] originPatterns = Arrays.stream(frontendUrls.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toArray(String[]::new);

        registry.addMapping("/**") // Apply the rule to every controller (/auth, /users, /appointments).
                .allowedOriginPatterns(originPatterns) // Trusted origins; wildcards are supported for previews.
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH") // Enabled HTTP methods.
                .allowedHeaders("*") // Allow any request header sent by the client.
                .exposedHeaders("Authorization") // Allow the frontend to read the JWT Authorization header.
                .allowCredentials(true) // Allow secure transport of cookies or session credentials when required.
                .maxAge(3600); // Cache this approval for one hour to avoid repeated OPTIONS requests.
    }
}