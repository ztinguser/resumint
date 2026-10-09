package com.resumint.export;

import java.time.Instant;
import java.util.UUID;

public record ExportJob(UUID id, String idempotencyKey, String credentialDigest, String snapshotRef,
        ExportStatus status, String leaseOwner, Instant leaseUntil, boolean cancelRequested,
        String resultRef, String errorCode, Instant createdAt, Instant updatedAt,
        Instant credentialExpiresAt, Instant resultExpiresAt) {
    public static ExportJob queued(UUID id, String idempotencyKey, String credentialDigest, String snapshotRef,
            Instant now, Instant credentialExpiresAt) {
        return new ExportJob(id, idempotencyKey, credentialDigest, snapshotRef, ExportStatus.QUEUED,
                null, null, false, null, null, now, now, credentialExpiresAt, null);
    }
}
