CREATE TABLE templates (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    category VARCHAR(40) NOT NULL,
    tags JSON NOT NULL,
    listed BOOLEAN NOT NULL DEFAULT FALSE,
    current_version INT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);

CREATE TABLE template_versions (
    template_id VARCHAR(64) NOT NULL,
    version INT NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    config_json JSON NOT NULL,
    component_versions JSON NOT NULL,
    asset_manifest JSON NOT NULL,
    status VARCHAR(20) NOT NULL,
    published_at TIMESTAMP(6) NULL,
    PRIMARY KEY (template_id, version),
    CONSTRAINT fk_template_versions_template FOREIGN KEY (template_id) REFERENCES templates(id),
    CONSTRAINT chk_template_version_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE TABLE export_jobs (
    id CHAR(36) PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    credential_digest CHAR(64) NOT NULL,
    snapshot_ref VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    lease_owner VARCHAR(96) NULL,
    lease_until TIMESTAMP(6) NULL,
    cancel_requested BOOLEAN NOT NULL DEFAULT FALSE,
    result_ref VARCHAR(255) NULL,
    error_code VARCHAR(80) NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    credential_expires_at TIMESTAMP(6) NOT NULL,
    result_expires_at TIMESTAMP(6) NULL,
    UNIQUE KEY uq_export_jobs_idempotency (idempotency_key),
    KEY idx_export_jobs_claim (status, lease_until, created_at),
    KEY idx_export_jobs_expiry (result_expires_at),
    CONSTRAINT chk_export_job_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED','EXPIRED'))
);

INSERT INTO templates (id, name, category, tags, listed, current_version)
VALUES ('benchmark-template', '排版基准模板', '极简', JSON_ARRAY('基准', '单栏', '双栏'), TRUE, 1);

INSERT INTO template_versions
    (template_id, version, schema_version, config_json, component_versions, asset_manifest, status, published_at)
VALUES
    ('benchmark-template', 1, '1.0',
     JSON_OBJECT(
       'layouts', JSON_ARRAY('single', 'two-column'),
       'editableParameters', JSON_OBJECT(
         'fontSize', JSON_OBJECT('min', 12, 'max', 18, 'step', 1, 'default', 14),
         'spacing', JSON_OBJECT('min', 0.8, 'max', 1.8, 'step', 0.1, 'default', 1.2),
         'marginMm', JSON_OBJECT('min', 10, 'max', 25, 'step', 1, 'default', 16)
       ),
       'fallback', JSON_OBJECT('unknownModule', '通用模块', 'unknownField', '文本字段')
     ),
     JSON_OBJECT('resume-page', 1, 'section', 1, 'portrait', 1, 'decoration', 1),
     JSON_ARRAY(), 'PUBLISHED', CURRENT_TIMESTAMP(6));
