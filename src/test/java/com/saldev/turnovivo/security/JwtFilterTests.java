package com.saldev.turnovivo.security;

import com.saldev.turnovivo.service.SessionService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtFilterTests {

    @Mock private JwtProvider jwtProvider;
    @Mock private SessionService sessionService;
    @Mock private FilterChain filterChain;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlyWhenJwtAndRedisSessionAreValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.validateToken("valid-token")).thenReturn(true);
        when(jwtProvider.getUserIdFromToken("valid-token")).thenReturn("user-id");
        when(jwtProvider.getRoleFromToken("valid-token")).thenReturn("SUPER_ADMIN");
        when(sessionService.isActive("user-id")).thenReturn(true);

        new JwtFilter(jwtProvider, sessionService).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("user-id");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SUPER_ADMIN");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void revokedSessionDoesNotAuthenticateRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer revoked-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(jwtProvider.validateToken("revoked-token")).thenReturn(true);
        when(jwtProvider.getUserIdFromToken("revoked-token")).thenReturn("user-id");
        when(sessionService.isActive("user-id")).thenReturn(false);

        new JwtFilter(jwtProvider, sessionService).doFilter(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain).doFilter(request, response);
    }
}
