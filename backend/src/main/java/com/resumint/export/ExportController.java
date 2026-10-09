package com.resumint.export;

import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/exports")
@Profile("!worker")
public final class ExportController {
    private final ExportCoordinator coordinator;
    private final Clock clock;

    public ExportController(ExportCoordinator coordinator, Clock clock) {
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<ExportResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreateExportRequest request) {
        try {
            ExportJob job = coordinator.submit(idempotencyKey, new ExportSnapshot(request.document()));
            return ResponseEntity.accepted()
                    .header(HttpHeaders.LOCATION, "/api/exports/" + job.id())
                    .body(ExportResponse.from(job));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{id}")
    public ExportResponse get(@PathVariable UUID id) {
        return ExportResponse.from(find(id));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<FileSystemResource> file(@PathVariable UUID id) {
        ExportJob job = find(id);
        if (job.status() != ExportStatus.COMPLETED || job.result() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "export is not complete");
        }
        if (job.resultExpiresAt() == null || !job.resultExpiresAt().isAfter(Instant.now(clock)) || !Files.isRegularFile(job.result())) {
            throw new ResponseStatusException(HttpStatus.GONE, "export result has expired");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume-" + id + ".pdf")
                .body(new FileSystemResource(job.result()));
    }

    private ExportJob find(UUID id) {
        try {
            return coordinator.get(id);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    public record CreateExportRequest(JsonNode document) { }

    public record ExportResponse(UUID id, String status, String errorCode, Instant createdAt, Instant resultExpiresAt) {
        static ExportResponse from(ExportJob job) {
            return new ExportResponse(job.id(), job.status().name().toLowerCase(Locale.ROOT),
                    job.errorCode(), job.createdAt(), job.resultExpiresAt());
        }
    }
}
