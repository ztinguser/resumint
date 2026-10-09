package com.resumint.export;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository {
    ExportJob save(ExportJob job);
    Optional<ExportJob> findById(UUID id);
    Optional<ExportJob> findByIdempotencyKey(String idempotencyKey);
    Optional<ExportJob> claimNext(String workerId, Instant now, Duration leaseDuration);
    long activeCount();
}
