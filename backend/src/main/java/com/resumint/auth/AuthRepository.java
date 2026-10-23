package com.resumint.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
    private final JdbcTemplate jdbc;
    AuthRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<UserRow> userByPhoneHash(String phoneHash) {
        return jdbc.query("SELECT id,phone_e164,password_hash,avatar,role,created_at,last_login_at FROM users WHERE phone_lookup_hash=?",
                (rs, row) -> new UserRow(UUID.fromString(rs.getString("id")), rs.getString("phone_e164"),
                        rs.getString("password_hash"), rs.getString("avatar"), rs.getString("role"), instant(rs, "created_at"), instant(rs, "last_login_at")), phoneHash)
                .stream().findFirst();
    }

    Optional<UserRow> userById(UUID id) {
        return jdbc.query("SELECT id,phone_e164,password_hash,avatar,role,created_at,last_login_at FROM users WHERE id=?",
                (rs, row) -> new UserRow(UUID.fromString(rs.getString("id")), rs.getString("phone_e164"),
                        rs.getString("password_hash"), rs.getString("avatar"), rs.getString("role"), instant(rs, "created_at"), instant(rs, "last_login_at")), id.toString())
                .stream().findFirst();
    }

    UserRow createUser(String phone, String phoneHash, Instant now) {
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO users(id,phone_e164,phone_lookup_hash,created_at,updated_at,last_login_at) VALUES(?,?,?,?,?,?)",
                    id.toString(), phone, phoneHash, at(now), at(now), at(now));
            return new UserRow(id, phone, null, "DEFAULT", "USER", now, now);
        } catch (DuplicateKeyException duplicate) {
            return userByPhoneHash(phoneHash).orElseThrow(() -> duplicate);
        }
    }

    void updateLastLogin(UUID id, Instant now) {
        jdbc.update("UPDATE users SET last_login_at=?,updated_at=? WHERE id=?", at(now), at(now), id.toString());
    }

    void updatePassword(UUID id, String passwordHash, Instant now) {
        jdbc.update("UPDATE users SET password_hash=?,updated_at=? WHERE id=?", passwordHash, at(now), id.toString());
    }

    void updateAvatar(UUID id, String avatar, Instant now) {
        jdbc.update("UPDATE users SET avatar=?,updated_at=? WHERE id=?", avatar, at(now), id.toString());
    }

    void updatePhone(UUID id, String phone, String phoneHash, Instant now) {
        jdbc.update("UPDATE users SET phone_e164=?,phone_lookup_hash=?,updated_at=? WHERE id=?",
                phone, phoneHash, at(now), id.toString());
    }

    void insertAttempt(AttemptRow row) {
        jdbc.update("INSERT INTO sms_verification_attempts(id,purpose,phone_lookup_hash,masked_phone,source_hash,ip_hash,device_hash,session_hash,provider,provider_out_id,status,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                row.id().toString(), row.purpose(), row.phoneHash(), row.maskedPhone(), row.sourceHash(), row.ipHash(),
                row.deviceHash(), row.sessionHash(), row.provider(), row.outId(), row.status(), at(row.createdAt()), at(row.expiresAt()));
    }

    Optional<AttemptRow> lockAttempt(UUID id) {
        return jdbc.query("SELECT id,purpose,phone_lookup_hash,masked_phone,source_hash,ip_hash,device_hash,session_hash,provider,provider_out_id,status,failure_count,created_at,expires_at FROM sms_verification_attempts WHERE id=? FOR UPDATE",
                (rs, row) -> new AttemptRow(UUID.fromString(rs.getString("id")), rs.getString("purpose"), rs.getString("phone_lookup_hash"),
                        rs.getString("masked_phone"), rs.getString("source_hash"), rs.getString("ip_hash"), rs.getString("device_hash"),
                        rs.getString("session_hash"), rs.getString("provider"), rs.getString("provider_out_id"),
                        rs.getString("status"), rs.getInt("failure_count"), instant(rs, "created_at"), instant(rs, "expires_at")), id.toString())
                .stream().findFirst();
    }

    void sentAttempt(UUID id, String requestId) {
        jdbc.update("UPDATE sms_verification_attempts SET status='SENT',provider_request_id=? WHERE id=?", requestId, id.toString());
    }

    void failedSend(UUID id, String code) {
        jdbc.update("UPDATE sms_verification_attempts SET status='SEND_FAILED',failure_code=? WHERE id=?", code, id.toString());
    }

    void failedVerification(UUID id, int failures, boolean locked) {
        jdbc.update("UPDATE sms_verification_attempts SET failure_count=?,status=?,failure_code='CODE_REJECTED' WHERE id=?",
                failures, locked ? "LOCKED" : "SENT", id.toString());
    }

    void consumeAttempt(UUID id, Instant now) {
        jdbc.update("UPDATE sms_verification_attempts SET status='CONSUMED',verified_at=?,consumed_at=?,failure_code=NULL WHERE id=? AND consumed_at IS NULL",
                at(now), at(now), id.toString());
    }

    long sentForPhoneSince(String phoneHash, Instant since) {
        return count("SELECT COUNT(*) FROM sms_verification_attempts WHERE phone_lookup_hash=? AND created_at>=? AND status IN ('PENDING','SENT','VERIFIED','CONSUMED')", phoneHash, at(since));
    }
    long sentForSourceSince(String sourceHash, Instant since) {
        return count("SELECT COUNT(*) FROM sms_verification_attempts WHERE source_hash=? AND created_at>=? AND status IN ('PENDING','SENT','VERIFIED','CONSUMED')", sourceHash, at(since));
    }
    long sentForDeviceSince(String deviceHash, Instant since) {
        if (deviceHash == null) return 0;
        return count("SELECT COUNT(*) FROM sms_verification_attempts WHERE device_hash=? AND created_at>=? AND status IN ('PENDING','SENT','VERIFIED','CONSUMED')", deviceHash, at(since));
    }
    long sentForSessionSince(String sessionHash, Instant since) {
        if (sessionHash == null) return 0;
        return count("SELECT COUNT(*) FROM sms_verification_attempts WHERE session_hash=? AND created_at>=? AND status IN ('PENDING','SENT','VERIFIED','CONSUMED')", sessionHash, at(since));
    }
    long sentSince(Instant since) {
        return count("SELECT COUNT(*) FROM sms_verification_attempts WHERE created_at>=? AND status IN ('PENDING','SENT','VERIFIED','CONSUMED')", at(since));
    }
    boolean createOperationalAlert(String type, Instant windowStart, long observed, long threshold, Instant now) {
        try {
            return jdbc.update("INSERT INTO operational_alerts(id,alert_type,window_start,observed_value,threshold_value,created_at) VALUES(?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), type, at(windowStart), observed, threshold, at(now)) == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }
    Optional<Instant> lastSentAt(String phoneHash) {
        List<Timestamp> values = jdbc.query("SELECT MAX(created_at) FROM sms_verification_attempts WHERE phone_lookup_hash=? AND status IN ('PENDING','SENT','VERIFIED','CONSUMED')",
                (rs, row) -> rs.getTimestamp(1), phoneHash);
        return values.isEmpty() || values.getFirst() == null ? Optional.empty() : Optional.of(values.getFirst().toInstant());
    }

    boolean acquireSmsLock(String phoneHash) {
        Integer value = jdbc.queryForObject("SELECT GET_LOCK(?, 2)", Integer.class, smsLockName(phoneHash));
        return value != null && value == 1;
    }
    void releaseSmsLock(String phoneHash) { jdbc.queryForObject("SELECT RELEASE_LOCK(?)", Integer.class, smsLockName(phoneHash)); }
    private static String smsLockName(String phoneHash) { return "rsms:" + phoneHash.substring(0, 58); }

    void revokeSessions(UUID userId, Instant now) {
        jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", at(now), userId.toString());
    }

    void revokeOtherSessions(UUID userId, UUID currentSessionId, Instant now) {
        jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE user_id=? AND id<>? AND revoked_at IS NULL",
                at(now), userId.toString(), currentSessionId.toString());
    }

    void revokeTrustedDevices(UUID userId, Instant now) {
        jdbc.update("UPDATE trusted_devices SET revoked_at=? WHERE user_id=? AND revoked_at IS NULL", at(now), userId.toString());
    }

    void deleteAccount(UUID userId, String phoneHash) {
        jdbc.update("DELETE FROM sms_verification_attempts WHERE phone_lookup_hash=?", phoneHash);
        jdbc.update("DELETE FROM users WHERE id=?", userId.toString());
    }

    long adminCount() {
        Long result = jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE role='ADMIN'", Long.class);
        return result == null ? 0 : result;
    }

    int promoteAdmin(UUID userId, Instant now) {
        return jdbc.update("UPDATE users SET role='ADMIN',updated_at=? WHERE id=? AND role<>'ADMIN'", at(now), userId.toString());
    }

    SessionTokens createSession(UUID userId, Instant now, Instant expiresAt) {
        String token = SecretDigests.token();
        String csrf = SecretDigests.token();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO auth_sessions(id,user_id,token_hash,csrf_hash,created_at,last_seen_at,expires_at) VALUES(?,?,?,?,?,?,?)",
                id.toString(), userId.toString(), SecretDigests.sha256(token), SecretDigests.sha256(csrf), at(now), at(now), at(expiresAt));
        return new SessionTokens(id, token, csrf, expiresAt);
    }

    Optional<SessionUser> sessionByTokenHash(String hash, Instant now) {
        return jdbc.query("SELECT s.id,s.user_id,s.csrf_hash,s.expires_at,u.phone_e164,u.role FROM auth_sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>?",
                (rs, row) -> new SessionUser(UUID.fromString(rs.getString("id")), UUID.fromString(rs.getString("user_id")),
                        rs.getString("csrf_hash"), instant(rs, "expires_at"), rs.getString("phone_e164"), rs.getString("role")), hash, at(now)).stream().findFirst();
    }

    void touchSession(UUID id, Instant now) { jdbc.update("UPDATE auth_sessions SET last_seen_at=? WHERE id=?", at(now), id.toString()); }
    void revokeSession(UUID id, Instant now) { jdbc.update("UPDATE auth_sessions SET revoked_at=? WHERE id=?", at(now), id.toString()); }

    DeviceToken createTrustedDevice(UUID userId, Instant now, Instant expiresAt) {
        String token = SecretDigests.token();
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO trusted_devices(id,user_id,token_hash,created_at,last_used_at,expires_at) VALUES(?,?,?,?,?,?)",
                id.toString(), userId.toString(), SecretDigests.sha256(token), at(now), at(now), at(expiresAt));
        return new DeviceToken(token, expiresAt);
    }

    boolean trustedDevice(UUID userId, String token, Instant now) {
        if (token == null || token.isBlank()) return false;
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM trusted_devices WHERE user_id=? AND token_hash=? AND revoked_at IS NULL AND expires_at>?",
                Integer.class, userId.toString(), SecretDigests.sha256(token), at(now));
        if (count != null && count > 0) {
            jdbc.update("UPDATE trusted_devices SET last_used_at=? WHERE user_id=? AND token_hash=?", at(now), userId.toString(), SecretDigests.sha256(token));
            return true;
        }
        return false;
    }

    private long count(String sql, Object... values) { Long result = jdbc.queryForObject(sql, Long.class, values); return result == null ? 0 : result; }
    private static Timestamp at(Instant value) { return Timestamp.from(value); }
    private static Instant instant(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        Timestamp value = rs.getTimestamp(column); return value == null ? null : value.toInstant();
    }

    record UserRow(UUID id, String phone, String passwordHash, String avatar, String role, Instant createdAt, Instant lastLoginAt) { }
    record AttemptRow(UUID id, String purpose, String phoneHash, String maskedPhone, String sourceHash, String ipHash,
                      String deviceHash, String sessionHash, String provider,
                      String outId, String status, int failureCount, Instant createdAt, Instant expiresAt) { }
    record SessionTokens(UUID id, String token, String csrfToken, Instant expiresAt) { }
    record SessionUser(UUID sessionId, UUID userId, String csrfHash, Instant expiresAt, String phone, String role) { }
    record DeviceToken(String token, Instant expiresAt) { }
}
