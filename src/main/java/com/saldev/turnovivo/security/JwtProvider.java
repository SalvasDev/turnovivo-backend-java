package com.saldev.turnovivo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtProvider {

    // Inject the secret key defined in application.yml.
    @Value("${app.jwt.secret}")
    private String jwtSecret;

    // Inject the expiration time (one day in milliseconds) defined in application.yml.
    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    /**
     * Generates a digitally signed token for a successfully authenticated user.
     * Stores the ID, email, and role in claims to avoid repeated PostgreSQL lookups.
     */
    public String generateToken(String userId, String email, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .subject(userId) // The "sub" claim is the token's principal subject.
                .claim("email", email) // Custom payload.
                .claim("role", role) // Payload required by the role guard.
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey()) // Cryptographically sign with the secret key.
                .compact();
    }

    /**
     * Extracts the user ID (subject) stored in the JWT.
     */
    public String getUserIdFromToken(String token) {
        return getClaims(token).getSubject();
    }

    /**
     * Extracts the user role stored in the JWT.
     */
    public String getRoleFromToken(String token) {
        return getClaims(token).get("role", String.class);
    }

    /**
     * Validates that the token is structurally correct, has a valid signature,
     * and has not expired.
     */
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Reject tokens that were altered, expired, or corrupted.
            return false;
        }
    }

    /**
     * Parses the token and safely extracts its claims payload.
     */
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Converts the JWT_SECRET string into a secure HMAC-SHA cryptographic key.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = this.jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
