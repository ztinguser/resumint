package com.resumint.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties={
        "resumint.sms.provider=fake", "resumint.sms.cooldown=60s", "resumint.sms.phone-hour-limit=5",
        "resumint.sms.daily-cost-warning-count=1",
        "resumint.auth.session-ttl=30d", "resumint.export.worker-enabled=false",
        "resumint.auth.phone-lookup-secrets=new-phone-lookup-secret-for-test-32-characters,old-phone-lookup-secret-for-test-32-characters"
})
@AutoConfigureMockMvc
@EnabledIfSystemProperty(named="resumint.mysql.it",matches="true")
class AccountIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:33306/resumint?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username",()->"resumint"); registry.add("spring.datasource.password",()->"resumint");
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AuthRepository repository;
    @Autowired SecurityAuditRepository audit;
    @Autowired SmsVerificationService sms;

    @BeforeEach void clean() {
        jdbc.update("DELETE FROM template_favorites"); jdbc.update("DELETE FROM resumes"); jdbc.update("DELETE FROM trusted_devices");
        jdbc.update("DELETE FROM auth_sessions"); jdbc.update("DELETE FROM sms_verification_attempts"); jdbc.update("DELETE FROM users");
        jdbc.update("DELETE FROM security_audit_events");
        jdbc.update("DELETE FROM operational_alerts");
    }

    @Test void smsCostAlertIsPersistentAndOpsStatusRequiresToken() throws Exception {
        sendCode("18800188000","LOGIN").andExpect(status().isOk());
        sendCode("18700187000","LOGIN").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operational_alerts WHERE alert_type='SMS_DAILY_COST'",Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT observed_value FROM operational_alerts WHERE alert_type='SMS_DAILY_COST'",Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT notification_status FROM operational_alerts WHERE alert_type='SMS_DAILY_COST'",String.class)).isEqualTo("PENDING");
        mvc.perform(get("/api/ops/status").header("X-Ops-Token","wrong"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OPS_TOKEN_INVALID"));
        String statusBody=mvc.perform(get("/api/ops/status").header("X-Ops-Token","dev-only-ops-token-change-me"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.smsCostAlertedToday").value(true))
                .andExpect(jsonPath("$.smsDailyCostWarningCount").value(1))
                .andExpect(jsonPath("$.alertEmailsPending").value(1))
                .andExpect(jsonPath("$.alertEmailsFailed").value(0)).andReturn().getResponse().getContentAsString();
        assertThat(statusBody).doesNotContain("18800188000","18700187000","phone","resume");
    }

    @Test void securityAuditRetentionKeepsBoundaryAndNewerEvents() {
        Instant cutoff = Instant.parse("2026-04-29T12:00:00Z");
        audit.record("SYSTEM", null, "OLDER_THAN_RETENTION", null, "{}", cutoff.minusSeconds(1));
        audit.record("SYSTEM", null, "AT_RETENTION_BOUNDARY", null, "{}", cutoff);
        audit.record("SYSTEM", null, "NEWER_THAN_RETENTION", null, "{}", cutoff.plusSeconds(1));

        assertThat(audit.deleteCreatedBefore(cutoff)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_events", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_events WHERE action='AT_RETENTION_BOUNDARY'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_events WHERE action='NEWER_THAN_RETENTION'", Integer.class)).isOne();
    }

    @Test void smsRegistrationIsOneTimeRateLimitedAndNeverStoresCode() throws Exception {
        MvcResult sent = sendCode("13800138000", "LOGIN").andExpect(status().isOk()).andReturn();
        UUID attemptId = UUID.fromString(json.readTree(sent.getResponse().getContentAsString()).get("attemptId").asText());
        sendCode("13800138000", "LOGIN").andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SMS_RATE_LIMITED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='sms_verification_attempts' AND column_name IN ('verify_code','code','code_hash','code_digest')", Integer.class)).isZero();

        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+attemptId+"\",\"phone\":\"13800138000\",\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SMS_CODE_INVALID"));
        MvcResult login = mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+attemptId+"\",\"phone\":\"+86 13800138000\",\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.registered").value(true)).andReturn();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE phone_e164='+8613800138000'", Integer.class)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM sms_verification_attempts WHERE id=?", String.class, attemptId.toString())).isEqualTo("CONSUMED");
        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+attemptId+"\",\"phone\":\"13800138000\",\"code\":\"123456\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SMS_ATTEMPT_USED"));

        Cookie session = cookie(login, AuthSessionFilter.SESSION_COOKIE);
        Cookie csrf = cookie(login, AuthSessionFilter.CSRF_COOKIE);
        mvc.perform(post("/api/auth/sms-login").cookie(session, csrf).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+attemptId+"\",\"phone\":\"13800138000\",\"code\":\"123456\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        mvc.perform(post("/api/auth/sms-login").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+attemptId+"\",\"phone\":\"13800138000\",\"code\":\"123456\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SMS_ATTEMPT_USED"));
        assertThat(session.getMaxAge()).isBetween((int) java.time.Duration.ofDays(29).toSeconds(),
                (int) java.time.Duration.ofDays(30).toSeconds());
        assertThat(csrf.getMaxAge()).isEqualTo(session.getMaxAge());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.maskedPhone").value("+86 138****8000"));
        mvc.perform(put("/api/me/password").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"abc123\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/password-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13800138000\",\"password\":\"abc123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.passwordSet").value(true));
        mvc.perform(put("/api/me/password").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"abc123\",\"newPassword\":\"ab123\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test void csrfAndExpiredSessionsAreRejected() throws Exception {
        MvcResult login = register("13900139000");
        Cookie session = cookie(login, AuthSessionFilter.SESSION_COOKIE);
        mvc.perform(put("/api/me/avatar").cookie(session).contentType(MediaType.APPLICATION_JSON).content("{\"avatar\":\"MALE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_REJECTED"));
        jdbc.update("UPDATE auth_sessions SET expires_at=?", java.sql.Timestamp.from(Instant.now().minusSeconds(1)));
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test void expiredSmsCodeCannotRegister() throws Exception {
        UUID attemptId = attempt(sendCode("15100151000", "LOGIN").andExpect(status().isOk()).andReturn());
        jdbc.update("UPDATE sms_verification_attempts SET expires_at=? WHERE id=?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)), attemptId.toString());
        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+attemptId+"\",\"phone\":\"15100151000\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SMS_CODE_EXPIRED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
    }

    @Test void passwordResetAndSmsTakeoverRequireTrustedDevice() throws Exception {
        MvcResult registration = register("13600136000");
        Cookie session = cookie(registration, AuthSessionFilter.SESSION_COOKIE);
        Cookie csrf = cookie(registration, AuthSessionFilter.CSRF_COOKIE);
        Cookie device = cookie(registration, AuthSessionFilter.DEVICE_COOKIE);
        mvc.perform(put("/api/me/password").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"original123\"}"))
                .andExpect(status().isOk());

        ageSmsAttempts();
        UUID riskyLogin = attempt(sendCode("13600136000", "LOGIN").andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+riskyLogin+"\",\"phone\":\"13600136000\",\"code\":\"123456\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PASSWORD_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM sms_verification_attempts WHERE id=?", String.class, riskyLogin.toString())).isEqualTo("CONSUMED");

        ageSmsAttempts();
        UUID resetAttempt = attempt(sendCode("13600136000", "PASSWORD_RESET").andExpect(status().isOk()).andReturn());
        mvc.perform(post("/api/auth/password-reset").cookie(device).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+resetAttempt+"\",\"phone\":\"13600136000\",\"code\":\"123456\",\"newPassword\":\"changed456\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/password-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13600136000\",\"password\":\"original123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/password-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13600136000\",\"password\":\"changed456\"}"))
                .andExpect(status().isOk());
    }

    @Test void databaseUniqueConstraintSurvivesConcurrentRegistration() throws Exception {
        String phone = PhoneNumbers.normalize("13700137000");
        String hash = sms.phoneHash(phone);
        CountDownLatch ready = new CountDownLatch(2); CountDownLatch go = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { ready.countDown(); go.await(); return repository.createUser(phone, hash, Instant.now()).id(); });
            var second = pool.submit(() -> { ready.countDown(); go.await(); return repository.createUser(phone, hash, Instant.now()).id(); });
            ready.await(); go.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE phone_e164=?", Integer.class, phone)).isOne();
    }

    @Test void authenticatedSmsRequestsAreHashedAndLimitedByDeviceAndSession() throws Exception {
        MvcResult registration = register("13000130000");
        Cookie session = cookie(registration, AuthSessionFilter.SESSION_COOKIE);
        Cookie device = cookie(registration, AuthSessionFilter.DEVICE_COOKIE);
        Cookie csrf = cookie(registration, AuthSessionFilter.CSRF_COOKIE);
        for (int index=0; index<10; index++) {
            String phone = String.format("1391000%04d", index);
            mvc.perform(post("/api/auth/sms-code").cookie(session, device, csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"phone\":\""+phone+"\",\"purpose\":\"PASSWORD_RESET\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_verification_attempts WHERE device_hash IS NOT NULL AND session_hash IS NOT NULL", Integer.class)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_verification_attempts WHERE device_hash=? OR session_hash=?", Integer.class,
                device.getValue(), session.getValue())).isZero();
        mvc.perform(post("/api/auth/sms-code").cookie(session, device, csrf).header("X-XSRF-TOKEN", csrf.getValue()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13910000010\",\"purpose\":\"PASSWORD_RESET\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("SMS_RATE_LIMITED"));
    }

    @Test void phoneChangeRequiresPasswordAndBothOldAndNewPhoneCodes() throws Exception {
        MvcResult registration = register("13500135000");
        Cookie session = cookie(registration, AuthSessionFilter.SESSION_COOKIE);
        Cookie csrf = cookie(registration, AuthSessionFilter.CSRF_COOKIE);
        mvc.perform(put("/api/me/password").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"original123\"}"))
                .andExpect(status().isOk());

        ageSmsAttempts();
        UUID oldAttempt = attempt(sendCode("13500135000", "PHONE_CHANGE_OLD").andExpect(status().isOk()).andReturn());
        UUID newAttempt = attempt(sendCode("13300133000", "PHONE_CHANGE_NEW").andExpect(status().isOk()).andReturn());
        String body = "{\"oldAttemptId\":\""+oldAttempt+"\",\"oldPhone\":\"13500135000\",\"oldCode\":\"123456\","+
                "\"newAttemptId\":\""+newAttempt+"\",\"newPhone\":\"13300133000\",\"newCode\":\"123456\",\"currentPassword\":\"wrong123\"}";
        mvc.perform(put("/api/me/phone").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        body = body.replace("wrong123", "original123");
        mvc.perform(put("/api/me/phone").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.maskedPhone").value("+86 133****3000"));
        mvc.perform(post("/api/auth/password-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13500135000\",\"password\":\"original123\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/password-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"13300133000\",\"password\":\"original123\"}"))
                .andExpect(status().isOk());
    }

    @Test void accountDeletionRequiresExplicitConfirmationAndRemovesAllAccountData() throws Exception {
        MvcResult registration = register("13200132000");
        Cookie session = cookie(registration, AuthSessionFilter.SESSION_COOKIE);
        Cookie csrf = cookie(registration, AuthSessionFilter.CSRF_COOKIE);
        mvc.perform(put("/api/me/password").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"delete123\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/me").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"delete123\",\"confirmation\":\"NO\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ACCOUNT_DELETE_CONFIRMATION_REQUIRED"));
        mvc.perform(delete("/api/me").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"delete123\",\"confirmation\":\"DELETE\"}"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM auth_sessions", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM trusted_devices", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_verification_attempts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM security_audit_events WHERE action='ACCOUNT_DELETED'", Integer.class)).isOne();
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test void passwordLoginMigratesAnOldPhoneLookupKeyWithoutChangingIdentity() throws Exception {
        MvcResult registration=register("15000150000");
        Cookie session=cookie(registration,AuthSessionFilter.SESSION_COOKIE);Cookie csrf=cookie(registration,AuthSessionFilter.CSRF_COOKIE);
        mvc.perform(put("/api/me/password").cookie(session,csrf).header("X-XSRF-TOKEN",csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\":\"rotate123\"}"))
                .andExpect(status().isOk());
        UUID userId=jdbc.queryForObject("SELECT id FROM users WHERE phone_e164='+8615000150000'",(rs,row)->UUID.fromString(rs.getString(1)));
        String oldHash=SecretDigests.hmac("old-phone-lookup-secret-for-test-32-characters",PhoneNumbers.normalize("15000150000"));
        jdbc.update("UPDATE users SET phone_lookup_hash=? WHERE id=?",oldHash,userId.toString());
        mvc.perform(post("/api/auth/password-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"15000150000\",\"password\":\"rotate123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(userId.toString()));
        String activeHash=SecretDigests.hmac("new-phone-lookup-secret-for-test-32-characters",PhoneNumbers.normalize("15000150000"));
        assertThat(jdbc.queryForObject("SELECT phone_lookup_hash FROM users WHERE id=?",String.class,userId.toString())).isEqualTo(activeHash);
    }

    private org.springframework.test.web.servlet.ResultActions sendCode(String phone, String purpose) throws Exception {
        return mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON)
                .content("{\"phone\":\""+phone+"\",\"purpose\":\""+purpose+"\"}"));
    }
    private MvcResult register(String phone) throws Exception {
        JsonNode sent = json.readTree(sendCode(phone,"LOGIN").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        return mvc.perform(post("/api/auth/sms-login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attemptId\":\""+sent.get("attemptId").asText()+"\",\"phone\":\""+phone+"\",\"code\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
    }
    private UUID attempt(MvcResult result) throws Exception {
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("attemptId").asText());
    }
    private void ageSmsAttempts() {
        jdbc.update("UPDATE sms_verification_attempts SET created_at=DATE_SUB(created_at, INTERVAL 2 MINUTE)");
    }
    private static Cookie cookie(MvcResult result, String name) {
        return Arrays.stream(result.getResponse().getCookies()).filter(value -> name.equals(value.getName())).findFirst().orElseThrow();
    }

    @TestConfiguration static class FakeSmsConfiguration {
        @Bean @Primary SmsProvider fakeSmsProvider() {
            return new SmsProvider() {
                @Override public String name() { return "fake"; }
                @Override public SendResult send(String phoneNational, String outId, String purpose) { return new SendResult("fake-request-"+outId); }
                @Override public boolean verify(String phoneNational, String outId, String code) { return "123456".equals(code); }
            };
        }
    }

}
