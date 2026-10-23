CREATE TABLE security_audit_events (
    id CHAR(36) PRIMARY KEY,
    actor_type VARCHAR(24) NOT NULL,
    actor_user_id CHAR(36) NULL,
    action VARCHAR(64) NOT NULL,
    target_user_id CHAR(36) NULL,
    details_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    KEY idx_security_audit_created (created_at),
    KEY idx_security_audit_target (target_user_id, created_at)
);

CREATE TABLE operational_alerts (
    id CHAR(36) PRIMARY KEY,
    alert_type VARCHAR(64) NOT NULL,
    window_start TIMESTAMP(6) NOT NULL,
    observed_value BIGINT NOT NULL,
    threshold_value BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    UNIQUE KEY uq_operational_alert_window (alert_type, window_start)
);

