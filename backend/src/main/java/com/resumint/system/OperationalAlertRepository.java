package com.resumint.system;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class OperationalAlertRepository {
    private final JdbcTemplate jdbc;

    OperationalAlertRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    boolean create(String type, Instant windowStart, long observed, long threshold, Instant now) {
        try {
            return jdbc.update("INSERT INTO operational_alerts(id,alert_type,window_start,observed_value,threshold_value,created_at,notification_status) VALUES(?,?,?,?,?,?,?)",
                    UUID.randomUUID().toString(), type, at(windowStart), observed, threshold, at(now), "PENDING") == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    @Transactional
    Optional<OperationalAlert> claimNext(Instant now, Duration lease, int maxAttempts) {
        var alerts=jdbc.query("""
                SELECT id,alert_type,window_start,observed_value,threshold_value,created_at,notification_attempts
                FROM operational_alerts
                WHERE notification_attempts < ? AND (
                    (notification_status IN ('PENDING','RETRY') AND
                        (notification_next_attempt_at IS NULL OR notification_next_attempt_at <= ?))
                    OR (notification_status='SENDING' AND notification_next_attempt_at <= ?)
                )
                ORDER BY created_at,id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs,row)->new OperationalAlert(UUID.fromString(rs.getString("id")),rs.getString("alert_type"),
                        rs.getTimestamp("window_start").toInstant(),rs.getLong("observed_value"),
                        rs.getLong("threshold_value"),rs.getTimestamp("created_at").toInstant(),
                        rs.getInt("notification_attempts")+1), maxAttempts, at(now), at(now));
        if(alerts.isEmpty())return Optional.empty();
        OperationalAlert alert=alerts.getFirst();
        jdbc.update("UPDATE operational_alerts SET notification_status='SENDING',notification_attempts=?,notification_last_attempt_at=?,notification_next_attempt_at=?,notification_error_code=NULL WHERE id=?",
                alert.notificationAttempts(),at(now),at(now.plus(lease)),alert.id().toString());
        return Optional.of(alert);
    }

    void sent(UUID id, Instant now) {
        jdbc.update("UPDATE operational_alerts SET notification_status='SENT',notified_at=?,notification_next_attempt_at=NULL,notification_error_code=NULL WHERE id=?",
                at(now),id.toString());
    }

    void failed(UUID id, int attempts, int maxAttempts, Instant nextAttempt, String errorCode) {
        String status=attempts>=maxAttempts?"FAILED":"RETRY";
        jdbc.update("UPDATE operational_alerts SET notification_status=?,notification_next_attempt_at=?,notification_error_code=? WHERE id=?",
                status,"FAILED".equals(status)?null:at(nextAttempt),errorCode,id.toString());
    }

    private static Timestamp at(Instant value){return Timestamp.from(value);}
}
