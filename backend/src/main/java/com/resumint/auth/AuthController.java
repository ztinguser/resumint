package com.resumint.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public final class AuthController {
    private final SmsVerificationService sms;
    private final AuthService auth;
    private final boolean secureCookies;

    AuthController(SmsVerificationService sms, AuthService auth,
            @Value("${resumint.auth.cookie-secure}") boolean secureCookies) {
        this.sms=sms; this.auth=auth; this.secureCookies=secureCookies;
    }

    @PostMapping("/auth/sms-code")
    SmsVerificationService.SendReceipt send(@Valid @RequestBody SendSmsRequest body, HttpServletRequest request) {
        return sms.send(body.phone(), body.purpose(), sources(request));
    }

    @PostMapping("/auth/sms-login")
    ResponseEntity<LoginResponse> smsLogin(@Valid @RequestBody SmsLoginRequest body, HttpServletRequest request) {
        AuthService.LoginResult result = auth.smsLogin(body.attemptId(), body.phone(), body.code(),
                AuthSessionFilter.cookie(request, AuthSessionFilter.DEVICE_COOKIE));
        return loginResponse(result);
    }

    @PostMapping("/auth/password-login")
    ResponseEntity<LoginResponse> passwordLogin(@Valid @RequestBody PasswordLoginRequest body) {
        return loginResponse(auth.passwordLogin(body.phone(), body.password()));
    }

    @PostMapping("/auth/password-reset")
    ResponseEntity<Map<String,Boolean>> passwordReset(@Valid @RequestBody PasswordResetRequest body, HttpServletRequest request) {
        auth.resetPassword(body.attemptId(), body.phone(), body.code(), body.newPassword(),
                AuthSessionFilter.cookie(request, AuthSessionFilter.DEVICE_COOKIE));
        return ResponseEntity.ok(Map.of("reset", true));
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        auth.logout(principal.sessionId());
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, clear(AuthSessionFilter.SESSION_COOKIE).toString())
                .header(HttpHeaders.SET_COOKIE, clear(AuthSessionFilter.CSRF_COOKIE).toString()).build();
    }

    @GetMapping("/me")
    MeResponse me(@AuthenticationPrincipal AuthPrincipal principal) {
        AuthRepository.UserRow user = auth.requireUser(principal.userId());
        return new MeResponse(user.id(), PhoneNumbers.mask(user.phone()), user.avatar(), user.passwordHash() != null);
    }

    @PutMapping("/me/password")
    ResponseEntity<Map<String,Boolean>> password(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody SetPasswordRequest body) {
        auth.setPassword(principal.userId(), body.currentPassword(), body.newPassword());
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PutMapping("/me/avatar")
    ResponseEntity<Map<String,Boolean>> avatar(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody AvatarRequest body) {
        auth.updateAvatar(principal.userId(), body.avatar());
        return ResponseEntity.ok(Map.of("updated", true));
    }

    @PutMapping("/me/phone")
    ResponseEntity<Map<String,Boolean>> phone(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody ChangePhoneRequest body) {
        auth.changePhone(principal.userId(), principal.sessionId(), body.oldAttemptId(), body.oldPhone(), body.oldCode(),
                body.newAttemptId(), body.newPhone(), body.newCode(), body.currentPassword());
        return ResponseEntity.ok(Map.of("updated", true));
    }

    private ResponseEntity<LoginResponse> loginResponse(AuthService.LoginResult result) {
        LoginResponse body = new LoginResponse(result.userId(), result.maskedPhone(), result.avatar(), result.passwordSet(), result.registered());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(AuthSessionFilter.SESSION_COOKIE, result.session().token(),
                        Duration.between(java.time.Instant.now(), result.session().expiresAt()), true).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(AuthSessionFilter.CSRF_COOKIE, result.session().csrfToken(),
                        Duration.between(java.time.Instant.now(), result.session().expiresAt()), false).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(AuthSessionFilter.DEVICE_COOKIE, result.device().token(),
                        Duration.between(java.time.Instant.now(), result.device().expiresAt()), true).toString())
                .body(body);
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge, boolean httpOnly) {
        return ResponseCookie.from(name, value).httpOnly(httpOnly).secure(secureCookies).sameSite("Lax").path("/")
                .maxAge(maxAge.isNegative() ? Duration.ZERO : maxAge).build();
    }
    private ResponseCookie clear(String name) { return cookie(name, "", Duration.ZERO, !AuthSessionFilter.CSRF_COOKIE.equals(name)); }
    private static SmsVerificationService.RateLimitSources sources(HttpServletRequest request) {
        return new SmsVerificationService.RateLimitSources(request.getRemoteAddr(),
                AuthSessionFilter.cookie(request, AuthSessionFilter.DEVICE_COOKIE),
                AuthSessionFilter.cookie(request, AuthSessionFilter.SESSION_COOKIE));
    }

    public record SendSmsRequest(@NotBlank String phone, @NotBlank String purpose) { }
    public record SmsLoginRequest(@NotNull UUID attemptId, @NotBlank String phone, @NotBlank String code) { }
    public record PasswordLoginRequest(@NotBlank String phone, @NotBlank String password) { }
    public record PasswordResetRequest(@NotNull UUID attemptId, @NotBlank String phone, @NotBlank String code, @NotBlank String newPassword) { }
    public record SetPasswordRequest(String currentPassword, @NotBlank String newPassword) { }
    public record AvatarRequest(@NotBlank String avatar) { }
    public record ChangePhoneRequest(@NotNull UUID oldAttemptId, @NotBlank String oldPhone, @NotBlank String oldCode,
            @NotNull UUID newAttemptId, @NotBlank String newPhone, @NotBlank String newCode, String currentPassword) { }
    public record LoginResponse(UUID userId, String maskedPhone, String avatar, boolean passwordSet, boolean registered) { }
    public record MeResponse(UUID userId, String maskedPhone, String avatar, boolean passwordSet) { }
}
