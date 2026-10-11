ALTER TABLE users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' AFTER avatar,
    ADD CONSTRAINT chk_user_role CHECK (role IN ('USER','ADMIN'));

ALTER TABLE templates
    CHANGE COLUMN name display_name VARCHAR(120) NOT NULL;

CREATE TABLE template_drafts (
    template_id VARCHAR(64) PRIMARY KEY,
    base_version INT NULL,
    schema_version VARCHAR(20) NOT NULL,
    config_json JSON NOT NULL,
    component_versions JSON NOT NULL,
    asset_manifest JSON NOT NULL,
    revision BIGINT NOT NULL DEFAULT 1,
    updated_by CHAR(36) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_template_draft_template FOREIGN KEY (template_id) REFERENCES templates(id),
    CONSTRAINT fk_template_draft_user FOREIGN KEY (updated_by) REFERENCES users(id)
);

CREATE TABLE template_audit_log (
    id CHAR(36) PRIMARY KEY,
    template_id VARCHAR(64) NOT NULL,
    actor_user_id CHAR(36) NOT NULL,
    action VARCHAR(40) NOT NULL,
    details_json JSON NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    KEY idx_template_audit (template_id, created_at),
    CONSTRAINT fk_template_audit_template FOREIGN KEY (template_id) REFERENCES templates(id),
    CONSTRAINT fk_template_audit_user FOREIGN KEY (actor_user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX uq_public_template_display_name ON templates(display_name);
