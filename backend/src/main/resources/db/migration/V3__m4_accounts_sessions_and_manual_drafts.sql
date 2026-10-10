CREATE TABLE users (
    id CHAR(36) PRIMARY KEY,
    phone_e164 VARCHAR(16) NOT NULL,
    phone_lookup_hash CHAR(64) NOT NULL,
    password_hash VARCHAR(100) NULL,
    avatar VARCHAR(16) NOT NULL DEFAULT 'DEFAULT',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    last_login_at TIMESTAMP(6) NULL,
    UNIQUE KEY uq_users_phone (phone_e164),
    UNIQUE KEY uq_users_phone_hash (phone_lookup_hash),
    CONSTRAINT chk_user_avatar CHECK (avatar IN ('DEFAULT','MALE','FEMALE'))
);

CREATE TABLE sms_verification_attempts (
    id CHAR(36) PRIMARY KEY,
    purpose VARCHAR(32) NOT NULL,
    phone_lookup_hash CHAR(64) NOT NULL,
    masked_phone VARCHAR(24) NOT NULL,
    source_hash CHAR(64) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_out_id VARCHAR(96) NOT NULL,
    provider_request_id VARCHAR(96) NULL,
    status VARCHAR(24) NOT NULL,
    failure_count INT NOT NULL DEFAULT 0,
    failure_code VARCHAR(80) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    verified_at TIMESTAMP(6) NULL,
    consumed_at TIMESTAMP(6) NULL,
    KEY idx_sms_phone_created (phone_lookup_hash, created_at),
    KEY idx_sms_source_created (source_hash, created_at),
    KEY idx_sms_created (created_at),
    CONSTRAINT chk_sms_status CHECK (status IN ('PENDING','SENT','SEND_FAILED','VERIFIED','CONSUMED','LOCKED','EXPIRED'))
);

CREATE TABLE auth_sessions (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    csrf_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_seen_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    UNIQUE KEY uq_auth_session_token (token_hash),
    KEY idx_auth_session_user (user_id, expires_at),
    CONSTRAINT fk_auth_session_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE trusted_devices (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    last_used_at TIMESTAMP(6) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    revoked_at TIMESTAMP(6) NULL,
    UNIQUE KEY uq_trusted_device_token (token_hash),
    KEY idx_trusted_device_user (user_id, expires_at),
    CONSTRAINT fk_trusted_device_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE resumes (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    title VARCHAR(120) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    template_version INT NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    document_json JSON NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    KEY idx_resumes_user_updated (user_id, updated_at),
    CONSTRAINT fk_resume_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_resume_template_version FOREIGN KEY (template_id, template_version) REFERENCES template_versions(template_id, version)
);

CREATE TABLE template_favorites (
    user_id CHAR(36) NOT NULL,
    template_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (user_id, template_id),
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_favorite_template FOREIGN KEY (template_id) REFERENCES templates(id) ON DELETE CASCADE
);
