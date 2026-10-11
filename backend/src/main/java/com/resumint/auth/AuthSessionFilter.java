package com.resumint.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class AuthSessionFilter extends OncePerRequestFilter {
    static final String SESSION_COOKIE = "resumint_session";
    static final String CSRF_COOKIE = "XSRF-TOKEN";
    static final String DEVICE_COOKIE = "resumint_device";
    private final AuthRepository repository;
    private final Clock clock;

    AuthSessionFilter(AuthRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = cookie(request, SESSION_COOKIE);
        if (token != null) {
            Instant now = clock.instant();
            repository.sessionByTokenHash(SecretDigests.sha256(token), now).ifPresent(session -> {
                AuthPrincipal principal = new AuthPrincipal(session.userId(), session.sessionId(), PhoneNumbers.mask(session.phone()), session.role());
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, session.csrfHash(),
                                java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + session.role()))));
                repository.touchSession(session.sessionId(), now);
            });
        }
        chain.doFilter(request, response);
    }

    static String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies()).filter(cookie -> name.equals(cookie.getName())).map(Cookie::getValue).findFirst().orElse(null);
    }
}
