ALTER TABLE operational_alerts
    ADD COLUMN notification_status VARCHAR(16) NULL,
    ADD COLUMN notification_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN notification_next_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN notification_last_attempt_at TIMESTAMP(6) NULL,
    ADD COLUMN notified_at TIMESTAMP(6) NULL,
    ADD COLUMN notification_error_code VARCHAR(64) NULL,
    ADD KEY idx_operational_alert_delivery (notification_status, notification_next_attempt_at, created_at);
