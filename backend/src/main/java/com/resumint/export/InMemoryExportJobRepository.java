package com.resumint.export;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Collectors;

public final class InMemoryExportJobRepository implements ExportJobRepository {
    private final Map<UUID, ExportJob> jobs = new LinkedHashMap<>();

    @Override public synchronized ExportJob insert(ExportJob job) { jobs.put(job.id(), job); return job; }
    @Override public synchronized Optional<ExportJob> findById(UUID id) { return Optional.ofNullable(jobs.get(id)); }
    @Override public synchronized Optional<ExportJob> findByIdempotencyKey(String key) { return jobs.values().stream().filter(job -> job.idempotencyKey().equals(key)).findFirst(); }
    @Override public synchronized Optional<ExportJob> claimNext(String workerId, Instant now, Duration leaseDuration) {
        for (ExportJob job : jobs.values()) {
            if (job.status() == ExportStatus.QUEUED || (job.status() == ExportStatus.RUNNING && job.leaseUntil() != null && !job.leaseUntil().isAfter(now))) {
                ExportJob claimed = copy(job, ExportStatus.RUNNING, workerId, now.plus(leaseDuration), job.cancelRequested(), job.resultRef(), job.errorCode(), now, job.resultExpiresAt());
                jobs.put(job.id(), claimed);
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }
    @Override public synchronized void succeed(UUID id, String workerId, String resultRef, Instant now, Instant expiresAt) {
        ExportJob job = requireLease(id, workerId);
        boolean cancelled = job.cancelRequested();
        jobs.put(id, copy(job, cancelled ? ExportStatus.CANCELLED : ExportStatus.SUCCEEDED, null, null,
                cancelled, cancelled ? null : resultRef, null, now, cancelled ? now : expiresAt));
    }
    @Override public synchronized void fail(UUID id, String workerId, String errorCode, Instant now) {
        ExportJob job = requireLease(id, workerId);
        jobs.put(id, copy(job, job.cancelRequested() ? ExportStatus.CANCELLED : ExportStatus.FAILED, null, null,
                job.cancelRequested(), null, errorCode, now, now));
    }
    @Override public synchronized ExportJob cancel(UUID id, Instant now) {
        ExportJob job = jobs.get(id);
        if (job == null) throw new IllegalArgumentException("export job not found");
        ExportStatus status = job.status() == ExportStatus.QUEUED ? ExportStatus.CANCELLED : job.status();
        ExportJob updated = copy(job, status, job.leaseOwner(), job.leaseUntil(), true, job.resultRef(), job.errorCode(), now,
                status == ExportStatus.CANCELLED ? now : job.resultExpiresAt());
        jobs.put(id, updated);
        return updated;
    }
    @Override public synchronized List<ExportJob> expireAndFindCleanupCandidates(Instant now) {
        List<ExportJob> result = new ArrayList<>();
        for (ExportJob original : List.copyOf(jobs.values())) {
            ExportJob job = original;
            boolean resultExpired = job.resultExpiresAt() != null && !job.resultExpiresAt().isAfter(now);
            boolean credentialExpired = !job.credentialExpiresAt().isAfter(now)
                    && (job.status() == ExportStatus.QUEUED || (job.status() == ExportStatus.RUNNING && job.leaseUntil() != null && !job.leaseUntil().isAfter(now)));
            if ((resultExpired && job.status() == ExportStatus.SUCCEEDED) || credentialExpired) {
                job = copy(job, ExportStatus.EXPIRED, null, null, job.cancelRequested(), job.resultRef(), job.errorCode(), now, now);
                jobs.put(job.id(), job);
            }
            if ((resultExpired || credentialExpired) && (job.snapshotRef() != null || job.resultRef() != null)) result.add(job);
        }
        return result;
    }
    @Override public synchronized void clearStorageReferences(UUID id, Instant now) {
        ExportJob job = jobs.get(id);
        jobs.put(id, new ExportJob(job.id(), job.idempotencyKey(), job.credentialDigest(), null, job.status(), null, null,
                job.cancelRequested(), null, job.errorCode(), job.createdAt(), now, job.credentialExpiresAt(), job.resultExpiresAt()));
    }
    @Override public synchronized long activeCount() { return jobs.values().stream().filter(job -> job.status() == ExportStatus.QUEUED || job.status() == ExportStatus.RUNNING).count(); }
    @Override public synchronized Set<String> referencedStorageRefs() {
        return jobs.values().stream().flatMap(job -> java.util.stream.Stream.of(job.snapshotRef(), job.resultRef()))
                .filter(java.util.Objects::nonNull).collect(Collectors.toUnmodifiableSet());
    }
    private ExportJob requireLease(UUID id, String workerId) {
        ExportJob job = jobs.get(id);
        if (job == null || job.status() != ExportStatus.RUNNING || !workerId.equals(job.leaseOwner())) throw new IllegalStateException("Worker does not own the export lease");
        return job;
    }
    private static ExportJob copy(ExportJob job, ExportStatus status, String owner, Instant leaseUntil,
            boolean cancelRequested, String resultRef, String errorCode, Instant updatedAt, Instant resultExpiresAt) {
        return new ExportJob(job.id(), job.idempotencyKey(), job.credentialDigest(), job.snapshotRef(), status, owner,
                leaseUntil, cancelRequested, resultRef, errorCode, job.createdAt(), updatedAt, job.credentialExpiresAt(), resultExpiresAt);
    }
}
