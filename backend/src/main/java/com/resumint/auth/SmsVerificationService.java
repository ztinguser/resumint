package com.resumint.auth;

import com.resumint.web.ApiException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import com.resumint.system.OperationalAlertService;

@Service
public class SmsVerificationService {
    private static final Logger log = LoggerFactory.getLogger(SmsVerificationService.class);
    private final AuthRepository repository;
    private final SmsProvider provider;
    private final Clock clock;
    private final List<String> lookupSecrets;
    private final Duration cooldown;
    private final Duration attemptTtl;
    private final int phoneHourLimit;
    private final int phoneDayLimit;
    private final int sourceHourLimit;
    private final int deviceHourLimit;
    private final int sessionHourLimit;
    private final int globalMinuteLimit;
    private final int verifyFailureLimit;
    private final int dailyCostWarningCount;
    private final OperationalAlertService alerts;

    SmsVerificationService(AuthRepository repository, SmsProvider provider, Clock clock, OperationalAlertService alerts,
            @Value("${resumint.auth.phone-lookup-secrets}") String lookupSecrets,
            @Value("${resumint.sms.cooldown}") Duration cooldown,
            @Value("${resumint.sms.attempt-ttl}") Duration attemptTtl,
            @Value("${resumint.sms.phone-hour-limit}") int phoneHourLimit,
            @Value("${resumint.sms.phone-day-limit}") int phoneDayLimit,
            @Value("${resumint.sms.source-hour-limit}") int sourceHourLimit,
            @Value("${resumint.sms.device-hour-limit}") int deviceHourLimit,
            @Value("${resumint.sms.session-hour-limit}") int sessionHourLimit,
            @Value("${resumint.sms.global-minute-limit}") int globalMinuteLimit,
            @Value("${resumint.sms.verify-failure-limit}") int verifyFailureLimit,
            @Value("${resumint.sms.daily-cost-warning-count}") int dailyCostWarningCount) {
        this.repository = repository; this.provider = provider; this.clock = clock; this.alerts=alerts;
        this.lookupSecrets = Arrays.stream(lookupSecrets.split(",")).map(String::trim).filter(value->!value.isEmpty()).toList();
        if(this.lookupSecrets.isEmpty()||this.lookupSecrets.stream().anyMatch(value->value.length()<32))throw new IllegalArgumentException("phone lookup key ring contains an invalid secret");
        this.cooldown = cooldown; this.attemptTtl = attemptTtl; this.phoneHourLimit = phoneHourLimit;
        this.phoneDayLimit = phoneDayLimit; this.sourceHourLimit = sourceHourLimit;
        this.deviceHourLimit = deviceHourLimit; this.sessionHourLimit = sessionHourLimit;
        this.globalMinuteLimit = globalMinuteLimit; this.verifyFailureLimit = verifyFailureLimit;
        this.dailyCostWarningCount = dailyCostWarningCount;
    }

    @Transactional(noRollbackFor=ApiException.class)
    SendReceipt send(String rawPhone, String purpose, RateLimitSources sources) {
        String phone = PhoneNumbers.normalize(rawPhone);
        String normalizedPurpose = purpose(purpose);
        String phoneHash = phoneHash(phone);
        String ipHash = hashSource(sources == null ? null : sources.ip());
        String deviceHash = hashSource(sources == null ? null : sources.deviceToken());
        String sessionHash = hashSource(sources == null ? null : sources.sessionToken());
        String sourceHash = ipHash;
        if (!repository.acquireSmsLock(phoneHash)) throw rateLimited("发送请求过于频繁，请稍后再试", 2);
        try {
            Instant now = clock.instant();
            repository.lastSentAt(phoneHash).ifPresent(last -> {
                Duration wait = Duration.between(now, last.plus(cooldown));
                if (!wait.isNegative() && !wait.isZero()) throw rateLimited("验证码发送过于频繁", Math.max(1, wait.toSeconds()));
            });
            if (repository.sentForPhoneSince(phoneHash, now.minus(Duration.ofHours(1))) >= phoneHourLimit
                    || repository.sentForPhoneSince(phoneHash, now.minus(Duration.ofDays(1))) >= phoneDayLimit
                    || repository.sentForSourceSince(sourceHash, now.minus(Duration.ofHours(1))) >= sourceHourLimit
                    || repository.sentForDeviceSince(deviceHash, now.minus(Duration.ofHours(1))) >= deviceHourLimit
                    || repository.sentForSessionSince(sessionHash, now.minus(Duration.ofHours(1))) >= sessionHourLimit
                    || repository.sentSince(now.minus(Duration.ofMinutes(1))) >= globalMinuteLimit) {
                throw rateLimited("验证码发送已达到限额，请稍后再试", cooldown.toSeconds());
            }
            UUID id = UUID.randomUUID();
            String outId = id.toString();
            AuthRepository.AttemptRow attempt = new AuthRepository.AttemptRow(id, normalizedPurpose, phoneHash,
                    PhoneNumbers.mask(phone), sourceHash, ipHash, deviceHash, sessionHash, provider.name(), outId, "PENDING", 0, now, now.plus(attemptTtl));
            repository.insertAttempt(attempt);
            try {
                SmsProvider.SendResult sent = provider.send(PhoneNumbers.national(phone), outId, normalizedPurpose);
                repository.sentAttempt(id, sent.providerRequestId());
                warnOnDailyCost(now);
                return new SendReceipt(id, attempt.maskedPhone(), cooldown.toSeconds(), attempt.expiresAt());
            } catch (RuntimeException exception) {
                repository.failedSend(id, exception instanceof ApiException api ? api.code() : "SMS_SEND_FAILED");
                throw exception;
            }
        } finally {
            repository.releaseSmsLock(phoneHash);
        }
    }

    @Transactional(noRollbackFor=ApiException.class)
    VerifiedPhone verify(UUID attemptId, String rawPhone, String expectedPurpose, String code) {
        String phone = PhoneNumbers.normalize(rawPhone);
        String phoneHash = phoneHash(phone);
        AuthRepository.AttemptRow attempt = repository.lockAttempt(attemptId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "SMS_ATTEMPT_NOT_FOUND", "验证码请求不存在"));
        Instant now = clock.instant();
        if (!attempt.phoneHash().equals(phoneHash) || !attempt.purpose().equals(purpose(expectedPurpose))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "SMS_ATTEMPT_MISMATCH", "验证码请求与当前操作不匹配");
        }
        if (attempt.expiresAt().isBefore(now)) throw new ApiException(HttpStatus.BAD_REQUEST, "SMS_CODE_EXPIRED", "验证码已过期");
        if (!"SENT".equals(attempt.status())) throw new ApiException(HttpStatus.CONFLICT, "SMS_ATTEMPT_USED", "验证码请求已失效或已使用");
        if (code == null || !code.matches("[0-9A-Za-z]{4,8}")) throw rejected(attempt);
        if (!provider.verify(PhoneNumbers.national(phone), attempt.outId(), code)) throw rejected(attempt);
        repository.consumeAttempt(attempt.id(), now);
        return new VerifiedPhone(phone, phoneHash, attempt.maskedPhone());
    }

    String phoneHash(String phone) { return SecretDigests.hmac(lookupSecrets.getFirst(), phone); }
    List<String> phoneHashes(String phone) { return lookupSecrets.stream().map(secret->SecretDigests.hmac(secret,phone)).distinct().toList(); }
    private void warnOnDailyCost(Instant now) {
        Instant dayStart = now.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        long sentToday = repository.sentSince(dayStart);
        if (sentToday >= dailyCostWarningCount
                && alerts.create("SMS_DAILY_COST", dayStart, sentToday, dailyCostWarningCount, now)) {
            log.warn("SMS_DAILY_COST_ALERT sent={} threshold={} windowStart={}", sentToday, dailyCostWarningCount, dayStart);
        }
    }
    private String hashSource(String source) { return source == null || source.isBlank() ? null : SecretDigests.hmac(lookupSecrets.getFirst(), source); }

    private ApiException rejected(AuthRepository.AttemptRow attempt) {
        int failures = attempt.failureCount() + 1;
        boolean locked = failures >= verifyFailureLimit;
        repository.failedVerification(attempt.id(), failures, locked);
        return new ApiException(HttpStatus.BAD_REQUEST, locked ? "SMS_ATTEMPT_LOCKED" : "SMS_CODE_INVALID",
                locked ? "验证码错误次数过多，请重新获取" : "验证码错误");
    }
    private static String purpose(String value) {
        String result = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("LOGIN", "PASSWORD_RESET", "PHONE_CHANGE_OLD", "PHONE_CHANGE_NEW").contains(result))
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_SMS_PURPOSE", "短信验证码用途无效");
        return result;
    }
    private static ApiException rateLimited(String message, long retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "SMS_RATE_LIMITED", message, Map.of("retryAfterSeconds", retryAfter));
    }
    record SendReceipt(UUID attemptId, String maskedPhone, long retryAfterSeconds, Instant expiresAt) { }
    record VerifiedPhone(String phone, String phoneHash, String maskedPhone) { }
    record RateLimitSources(String ip, String deviceToken, String sessionToken) { }
}
