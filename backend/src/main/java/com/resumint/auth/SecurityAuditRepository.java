package com.resumint.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class SecurityAuditRepository {
    private final JdbcTemplate jdbc;

    SecurityAuditRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    void record(String actorType, UUID actorUserId, String action, UUID targetUserId, String detailsJson, Instant now) {
        jdbc.update("INSERT INTO security_audit_events(id,actor_type,actor_user_id,action,target_user_id,details_json,created_at) VALUES(?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), actorType, id(actorUserId), action, id(targetUserId), detailsJson, Timestamp.from(now));
    }

    int deleteCreatedBefore(Instant cutoff) {
        return jdbc.update("DELETE FROM security_audit_events WHERE created_at < ?", Timestamp.from(cutoff));
    }

    private static String id(UUID value) { return value == null ? null : value.toString(); }
}

