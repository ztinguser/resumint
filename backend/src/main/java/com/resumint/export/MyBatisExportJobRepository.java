package com.resumint.export;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

public class MyBatisExportJobRepository implements ExportJobRepository {
    private final ExportJobMapper mapper;
    public MyBatisExportJobRepository(ExportJobMapper mapper) { this.mapper = mapper; }
    @Override public ExportJob insert(ExportJob job) { mapper.insert(job); return job; }
    @Override public Optional<ExportJob> findById(UUID id) { return Optional.ofNullable(mapper.findById(id)); }
    @Override public Optional<ExportJob> findByIdempotencyKey(String key) { return Optional.ofNullable(mapper.findByIdempotencyKey(key)); }
    @Override @Transactional public Optional<ExportJob> claimNext(String workerId, Instant now, Duration leaseDuration) {
        UUID id = mapper.findClaimCandidate(now);
        if (id == null || mapper.claim(id, workerId, now, now.plus(leaseDuration)) != 1) return Optional.empty();
        return findById(id);
    }
    @Override public void succeed(UUID id,String owner,String ref,Instant now,Instant expiresAt) { if (mapper.succeed(id,owner,ref,now,expiresAt)!=1) throw new IllegalStateException("Worker does not own the export lease"); }
    @Override public void fail(UUID id,String owner,String code,Instant now) { if (mapper.fail(id,owner,code,now)!=1) throw new IllegalStateException("Worker does not own the export lease"); }
    @Override public ExportJob cancel(UUID id,Instant now) { if (mapper.cancel(id,now)!=1) throw new IllegalArgumentException("export job not found"); return findById(id).orElseThrow(); }
    @Override @Transactional public List<ExportJob> expireAndFindCleanupCandidates(Instant now) { mapper.expire(now); return mapper.cleanupCandidates(now); }
    @Override public void clearStorageReferences(UUID id,Instant now) { mapper.clearRefs(id,now); }
    @Override public long activeCount() { return mapper.activeCount(); }
}
