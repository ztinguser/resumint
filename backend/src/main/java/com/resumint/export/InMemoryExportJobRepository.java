package com.resumint.export;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryExportJobRepository implements ExportJobRepository {
    private final Map<UUID, ExportJob> jobs = new LinkedHashMap<>();

    @Override
    public synchronized ExportJob save(ExportJob job) {
        jobs.put(job.id(), job);
        return job;
    }

    @Override
    public synchronized Optional<ExportJob> findById(UUID id) {
        return Optional.ofNullable(jobs.get(id));
    }

    @Override
    public synchronized Optional<ExportJob> findByIdempotencyKey(String idempotencyKey) {
        return jobs.values().stream().filter(job -> job.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    @Override
    public synchronized Optional<ExportJob> claimNext(String workerId, Instant now, Duration leaseDuration) {
        return jobs.values().stream()
                .filter(job -> job.claim(workerId, now, now.plus(leaseDuration)))
                .findFirst();
    }

    @Override
    public synchronized long activeCount() {
        return jobs.values().stream().filter(job -> job.status() == ExportStatus.QUEUED || job.status() == ExportStatus.RUNNING).count();
    }
}
