package com.resumint.export;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public final class ExportJob {
    private final UUID id;
    private final String idempotencyKey;
    private final ExportSnapshot snapshot;
    private final Instant createdAt;
    private ExportStatus status = ExportStatus.QUEUED;
    private String leaseOwner;
    private Instant leaseUntil;
    private Path result;
    private Instant resultExpiresAt;
    private String errorCode;

    public ExportJob(UUID id, String idempotencyKey, ExportSnapshot snapshot, Instant createdAt) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.snapshot = snapshot;
        this.createdAt = createdAt;
    }

    public synchronized boolean claim(String workerId, Instant now, Instant until) {
        if (status == ExportStatus.QUEUED || (status == ExportStatus.RUNNING && leaseUntil != null && !leaseUntil.isAfter(now))) {
            status = ExportStatus.RUNNING;
            leaseOwner = workerId;
            leaseUntil = until;
            return true;
        }
        return false;
    }

    public synchronized void complete(String workerId, Path result, Instant expiresAt) {
        requireLease(workerId);
        this.status = ExportStatus.COMPLETED;
        this.result = result;
        this.resultExpiresAt = expiresAt;
        this.leaseUntil = null;
    }

    public synchronized void fail(String workerId, String errorCode) {
        requireLease(workerId);
        this.status = ExportStatus.FAILED;
        this.errorCode = errorCode;
        this.leaseUntil = null;
    }

    private void requireLease(String workerId) {
        if (status != ExportStatus.RUNNING || !workerId.equals(leaseOwner)) {
            throw new IllegalStateException("Worker does not own the export lease");
        }
    }

    public UUID id() { return id; }
    public String idempotencyKey() { return idempotencyKey; }
    public ExportSnapshot snapshot() { return snapshot; }
    public Instant createdAt() { return createdAt; }
    public synchronized ExportStatus status() { return status; }
    public synchronized String leaseOwner() { return leaseOwner; }
    public synchronized Instant leaseUntil() { return leaseUntil; }
    public synchronized Path result() { return result; }
    public synchronized Instant resultExpiresAt() { return resultExpiresAt; }
    public synchronized String errorCode() { return errorCode; }
}
