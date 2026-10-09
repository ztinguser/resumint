package com.resumint.export;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

public final class ExportCoordinator {
    private final ExportJobRepository repository;
    private final ResumeDocumentSchema schema;
    private final ExportLimits limits;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ExportCoordinator(ExportJobRepository repository, ResumeDocumentSchema schema, ExportLimits limits, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.schema = schema;
        this.limits = limits;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public synchronized ExportJob submit(String idempotencyKey, ExportSnapshot snapshot) {
        if (idempotencyKey == null || idempotencyKey.length() < 8 || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotency key must contain 8 to 128 characters");
        }
        var existing = repository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) return existing.get();
        schema.validateTopLevel(snapshot.document());
        if (objectMapper.writeValueAsBytes(snapshot.document()).length > limits.maxSnapshotBytes()) {
            throw new IllegalArgumentException("snapshot exceeds configured size limit");
        }
        if (repository.activeCount() >= limits.queueCapacity()) {
            throw new IllegalStateException("export queue is full");
        }
        return repository.save(new ExportJob(UUID.randomUUID(), idempotencyKey, snapshot, Instant.now(clock)));
    }

    public ExportJob get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("export job not found"));
    }
}
