package com.resumint.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class SessionCsrfFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthPrincipal && unsafe(request.getMethod())) {
            String header = request.getHeader("X-XSRF-TOKEN");
            String cookie = AuthSessionFilter.cookie(request, AuthSessionFilter.CSRF_COOKIE);
            String expectedHash = String.valueOf(authentication.getCredentials());
            if (header == null || cookie == null || !header.equals(cookie) || !SecretDigests.sha256(header).equals(expectedHash)) {
                response.setStatus(403);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("{\"code\":\"CSRF_REJECTED\",\"message\":\"请求安全令牌无效\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
    private static boolean unsafe(String method) { return !("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)); }
}
