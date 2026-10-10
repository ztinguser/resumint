ALTER TABLE sms_verification_attempts
    ADD COLUMN ip_hash CHAR(64) NULL AFTER source_hash,
    ADD COLUMN device_hash CHAR(64) NULL AFTER ip_hash,
    ADD COLUMN session_hash CHAR(64) NULL AFTER device_hash,
    ADD KEY idx_sms_ip_created (ip_hash, created_at),
    ADD KEY idx_sms_device_created (device_hash, created_at),
    ADD KEY idx_sms_session_created (session_hash, created_at);
