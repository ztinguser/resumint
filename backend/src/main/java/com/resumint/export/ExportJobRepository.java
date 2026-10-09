package com.resumint.export;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository {
    ExportJob insert(ExportJob job);
    Optional<ExportJob> findById(UUID id);
    Optional<ExportJob> findByIdempotencyKey(String idempotencyKey);
    Optional<ExportJob> claimNext(String workerId, Instant now, Duration leaseDuration);
    void succeed(UUID id, String workerId, String resultRef, Instant now, Instant resultExpiresAt);
    void fail(UUID id, String workerId, String errorCode, Instant now);
    ExportJob cancel(UUID id, Instant now);
    List<ExportJob> expireAndFindCleanupCandidates(Instant now);
    void clearStorageReferences(UUID id, Instant now);
    long activeCount();
}
