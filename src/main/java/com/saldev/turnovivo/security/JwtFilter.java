package com.saldev.turnovivo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import com.saldev.turnovivo.service.SessionService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtProvider jwtProvider;
    private final SessionService sessionService;

    // Constructor injection keeps dependencies explicit and decoupled.
    public JwtFilter(JwtProvider jwtProvider, SessionService sessionService) {
        this.jwtProvider = jwtProvider;
        this.sessionService = sessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Extract the Authorization header from the HTTP request.
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // Remove the "Bearer " prefix to obtain the raw token string.

            // 2. Cryptographically validate the token at the application boundary.
            if (jwtProvider.validateToken(token)) {
                String userId = jwtProvider.getUserIdFromToken(token);
                if (!sessionService.isActive(userId)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                String role = jwtProvider.getRoleFromToken(token);
                // Spring Security convention: internal roles require the ROLE_ prefix (for example, ROLE_CUSTOMER).
                String grantedRole = "ROLE_" + role;
                List<SimpleGrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority(grantedRole));

                // 3. Create the authentication object with the user ID and granted roles.
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // 4. Register the user in the security context for the current thread.
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 5. Continue the request through the next filter or controller.
        filterChain.doFilter(request, response);
    }
}
