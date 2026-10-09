ALTER TABLE export_jobs
    MODIFY snapshot_ref VARCHAR(512) NULL,
    MODIFY result_ref VARCHAR(512) NULL;
