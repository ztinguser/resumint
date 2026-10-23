package com.resumint.export;

import com.resumint.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

public final class ExportCoordinator {
    private final ExportJobRepository repository;
    private final ResumeDocumentSchema schema;
    private final ExportLimits limits;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ExportStorage storage;
    private final ExportCredentialService credentials;
    public ExportCoordinator(ExportJobRepository repository,ResumeDocumentSchema schema,ExportLimits limits,ObjectMapper objectMapper,Clock clock,ExportStorage storage,ExportCredentialService credentials) {
        this.repository=repository; this.schema=schema; this.limits=limits; this.objectMapper=objectMapper; this.clock=clock; this.storage=storage; this.credentials=credentials;
    }
    public synchronized Submission submit(String key,ExportSnapshot snapshot) {
        if(key==null || key.length()<8 || key.length()>128) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_IDEMPOTENCY_KEY","幂等键长度必须为 8 到 128 个字符");
        var existing=repository.findByIdempotencyKey(key);
        if(existing.isPresent()) return submission(existing.get());
        try { schema.validateTopLevel(snapshot.document()); } catch(IllegalArgumentException e) { throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_DOCUMENT",e.getMessage()); }
        int bytes=objectMapper.writeValueAsBytes(snapshot.document()).length;
        if(bytes>limits.maxSnapshotBytes()) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,"EXPORT_LIMIT_EXCEEDED","文档快照超过导出限制",Map.of("limit","maxSnapshotBytes","allowed",limits.maxSnapshotBytes(),"actual",bytes));
        if(repository.activeCount()>=limits.queueCapacity()) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,"EXPORT_QUEUE_FULL","导出队列已满",Map.of("limit","queueCapacity","allowed",limits.queueCapacity()));
        Instant now=Instant.now(clock); UUID id=UUID.randomUUID(); String token=credentials.issue(id); String ref=storage.writeSnapshot(id,snapshot.document());
        try { return new Submission(repository.insert(ExportJob.queued(id,key,credentials.digest(token),ref,now,now.plus(limits.credentialTtl()))),token); }
        catch(RuntimeException e) { storage.deleteQuietly(ref); var raced=repository.findByIdempotencyKey(key); if(raced.isPresent()) return submission(raced.get()); throw e; }
    }
    public ExportJob authorized(UUID id,String credential) {
        ExportJob job=repository.findById(id).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"EXPORT_NOT_FOUND","导出任务不存在"));
        if(!job.credentialExpiresAt().isAfter(Instant.now(clock)) || !credentials.matches(credential,job.credentialDigest())) throw new ApiException(HttpStatus.FORBIDDEN,"EXPORT_CREDENTIAL_INVALID","游客导出凭据无效或已过期");
        return job;
    }
    public ExportJob cancel(UUID id,String credential) { authorized(id,credential); return repository.cancel(id,Instant.now(clock)); }
    private Submission submission(ExportJob job) { return new Submission(job,credentials.issue(job.id(),job.credentialDigest())); }
    public record Submission(ExportJob job,String credential) { }
}
