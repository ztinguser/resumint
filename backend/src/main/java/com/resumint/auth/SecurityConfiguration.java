package com.resumint.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.http.MediaType;
import org.springframework.http.HttpMethod;

@Configuration
class SecurityConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean
    @Profile("!worker")
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthSessionFilter sessionFilter, SessionCsrfFilter csrfFilter) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"code\":\"SESSION_EXPIRED\",\"message\":\"登录已过期，请重新登录\"}");
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403); response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write("{\"code\":\"ACCESS_DENIED\",\"message\":\"无权执行此操作\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/sms-code", "/api/auth/sms-login", "/api/auth/password-login", "/api/auth/password-reset").permitAll()
                        .requestMatchers("/api/templates/favorites", "/api/templates/*/favorite").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET,"/api/templates/**").permitAll()
                        .requestMatchers("/api/exports/**", "/api/health", "/api/ops/status", "/api/openapi.json", "/error").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(sessionFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(csrfFilter, AuthSessionFilter.class)
                .build();
    }
}
