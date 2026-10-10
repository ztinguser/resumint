package com.resumint.auth;

import com.resumint.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final AuthRepository repository;
    private final SmsVerificationService sms;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final Duration sessionTtl;
    private final Duration trustedDeviceTtl;
    private final Duration inactivityRiskAge;

    AuthService(AuthRepository repository, SmsVerificationService sms, PasswordEncoder passwordEncoder, Clock clock,
            @Value("${resumint.auth.session-ttl}") Duration sessionTtl,
            @Value("${resumint.auth.trusted-device-ttl}") Duration trustedDeviceTtl,
            @Value("${resumint.auth.inactivity-risk-age}") Duration inactivityRiskAge) {
        this.repository=repository; this.sms=sms; this.passwordEncoder=passwordEncoder; this.clock=clock;
        this.sessionTtl=sessionTtl; this.trustedDeviceTtl=trustedDeviceTtl; this.inactivityRiskAge=inactivityRiskAge;
    }

    @Transactional(noRollbackFor=ApiException.class)
    LoginResult smsLogin(UUID attemptId, String phone, String code, String trustedDeviceToken) {
        SmsVerificationService.VerifiedPhone verified = sms.verify(attemptId, phone, "LOGIN", code);
        Instant now = clock.instant();
        var existing = repository.userByPhoneHash(verified.phoneHash());
        AuthRepository.UserRow user;
        boolean registered = existing.isEmpty();
        if (registered) {
            user = repository.createUser(verified.phone(), verified.phoneHash(), now);
        } else {
            user = existing.orElseThrow();
            boolean trusted = repository.trustedDevice(user.id(), trustedDeviceToken, now);
            boolean inactive = user.lastLoginAt() == null || user.lastLoginAt().isBefore(now.minus(inactivityRiskAge));
            if (!trusted || inactive) {
                throw new ApiException(HttpStatus.FORBIDDEN, user.passwordHash() == null ? "ACCOUNT_RECOVERY_REQUIRED" : "PASSWORD_REQUIRED",
                        user.passwordHash() == null ? "此设备不能仅凭短信接管账号，请走人工恢复" : "新设备或长期未登录，请使用原密码登录");
            }
        }
        return establish(user, now, registered);
    }

    @Transactional
    LoginResult passwordLogin(String rawPhone, String password) {
        String phone = PhoneNumbers.normalize(rawPhone);
        Instant now = clock.instant();
        AuthRepository.UserRow user = repository.userByPhoneHash(sms.phoneHash(phone))
                .orElseThrow(AuthService::invalidCredentials);
        if (user.passwordHash() == null || !passwordEncoder.matches(password == null ? "" : password, user.passwordHash())) throw invalidCredentials();
        return establish(user, now, false);
    }

    @Transactional
    void setPassword(UUID userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        AuthRepository.UserRow user = repository.userById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "登录已过期"));
        if (user.passwordHash() != null && !passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.passwordHash())) throw invalidCredentials();
        repository.updatePassword(userId, passwordEncoder.encode(newPassword), clock.instant());
    }

    @Transactional(noRollbackFor=ApiException.class)
    void resetPassword(UUID attemptId, String phone, String code, String newPassword, String trustedDeviceToken) {
        validatePassword(newPassword);
        SmsVerificationService.VerifiedPhone verified = sms.verify(attemptId, phone, "PASSWORD_RESET", code);
        Instant now = clock.instant();
        AuthRepository.UserRow user = repository.userByPhoneHash(verified.phoneHash())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号不存在"));
        if (!repository.trustedDevice(user.id(), trustedDeviceToken, now)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_RECOVERY_REQUIRED", "非可信设备不能重置密码，请走人工恢复");
        }
        repository.updatePassword(user.id(), passwordEncoder.encode(newPassword), now);
        repository.revokeSessions(user.id(), now);
    }

    @Transactional
    void logout(UUID sessionId) { repository.revokeSession(sessionId, clock.instant()); }

    @Transactional
    void updateAvatar(UUID userId, String avatar) {
        String value = avatar == null ? "" : avatar.toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("DEFAULT", "MALE", "FEMALE").contains(value))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AVATAR", "头像选项无效");
        repository.updateAvatar(userId, value, clock.instant());
    }

    AuthRepository.UserRow requireUser(UUID userId) {
        return repository.userById(userId).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_EXPIRED", "登录已过期"));
    }

    private LoginResult establish(AuthRepository.UserRow user, Instant now, boolean registered) {
        repository.updateLastLogin(user.id(), now);
        AuthRepository.SessionTokens session = repository.createSession(user.id(), now, now.plus(sessionTtl));
        AuthRepository.DeviceToken device = repository.createTrustedDevice(user.id(), now, now.plus(trustedDeviceTtl));
        return new LoginResult(user.id(), PhoneNumbers.mask(user.phone()), user.avatar(), user.passwordHash() != null,
                registered, session, device);
    }
    private static ApiException invalidCredentials() { return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "手机号或密码错误"); }
    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码需为 8–72 位并同时包含字母和数字");
    }
    record LoginResult(UUID userId, String maskedPhone, String avatar, boolean passwordSet, boolean registered,
                       AuthRepository.SessionTokens session, AuthRepository.DeviceToken device) { }
}
