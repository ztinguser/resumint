package com.resumint.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ExportPipelineTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sharedSchemaIsPackagedAndIdempotentSubmissionsReuseTheJob() throws Exception {
        var repository = new InMemoryExportJobRepository();
        var schema = new ResumeDocumentSchema(objectMapper);
        var limits = ExportLimits.m1Defaults(URI.create("http://127.0.0.1:3100"));
        var coordinator = new ExportCoordinator(repository, schema, limits, objectMapper, fixedClock());
        var snapshot = new ExportSnapshot(validDocument());

        ExportJob first = coordinator.submit("stable-key-01", snapshot);
        ExportJob repeated = coordinator.submit("stable-key-01", snapshot);

        assertThat(schema.schema().path("$id").asString()).isEqualTo("https://resumint.local/schema/resume-document-1.0.json");
        assertThat(repeated.id()).isEqualTo(first.id());
        assertThat(repository.activeCount()).isEqualTo(1);
    }

    @Test
    void expiredLeaseCanBeRecoveredByAnotherWorker() throws Exception {
        var repository = new InMemoryExportJobRepository();
        var job = repository.save(new ExportJob(UUID.randomUUID(), "stable-key-02", new ExportSnapshot(validDocument()), NOW));

        assertThat(repository.claimNext("worker-a", NOW, Duration.ofSeconds(60))).contains(job);
        assertThat(repository.claimNext("worker-b", NOW.plusSeconds(59), Duration.ofSeconds(60))).isEmpty();
        assertThat(repository.claimNext("worker-b", NOW.plusSeconds(61), Duration.ofSeconds(60))).contains(job);
        assertThat(job.leaseOwner()).isEqualTo("worker-b");
    }

    @Test
    void workerCompletesAClaimedSnapshotAndPublishesOnlyTheFinalFile(@TempDir java.nio.file.Path directory) throws Exception {
        var repository = new InMemoryExportJobRepository();
        var limits = ExportLimits.m1Defaults(URI.create("http://127.0.0.1:3100"));
        ExportJob job = repository.save(new ExportJob(UUID.randomUUID(), "stable-key-03", new ExportSnapshot(validDocument()), NOW));
        PdfRenderer renderer = (snapshot, output) -> Files.writeString(output, "%PDF-test", StandardCharsets.US_ASCII);
        var worker = new ExportWorker(repository, renderer, limits, fixedClock(), directory);

        assertThat(worker.runOnce("worker-test")).isTrue();
        assertThat(job.status()).isEqualTo(ExportStatus.COMPLETED);
        assertThat(job.result()).exists().hasFileName(job.id() + ".pdf");
        assertThat(directory.resolve(job.id() + ".pdf.tmp")).doesNotExist();
        assertThat(job.resultExpiresAt()).isEqualTo(NOW.plus(limits.resultRetention()));
        assertThat(worker.runOnce("worker-test")).isFalse();
    }

    @Test
    void rejectsInvalidSchemaAndOversizedSnapshots() throws Exception {
        var repository = new InMemoryExportJobRepository();
        var limits = new ExportLimits(URI.create("http://127.0.0.1:3100"), 1, 20, 8,
                Duration.ofSeconds(45), Duration.ofSeconds(60), Duration.ofMinutes(15));
        var coordinator = new ExportCoordinator(repository, new ResumeDocumentSchema(objectMapper), limits, objectMapper, fixedClock());

        assertThatThrownBy(() -> coordinator.submit("stable-key-04", new ExportSnapshot(objectMapper.readTree("{}"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> coordinator.submit("stable-key-05", new ExportSnapshot(validDocument())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size");
    }

    private JsonNode validDocument() throws Exception {
        return objectMapper.readTree("""
                {
                  "schemaVersion":"1.0",
                  "documentId":"test-document",
                  "template":{"id":"benchmark-template","version":1},
                  "locale":"zh-CN",
                  "profile":{"fullName":"林知夏","headline":"异步导出验证","email":"test@example.com","website":{"label":"作品集","href":"https://example.com/portfolio"}},
                  "layout":{"kind":"single","page":{"size":"A4","marginMm":16},"theme":{"fontFamily":"Arial","accentColor":"#244b45"},"decorations":[]},
                  "sections":[{"id":"test-section","type":"custom","title":"Java Playwright PDF","column":"main","visible":true,"items":[{"id":"test-item","title":"不可变快照","summary":"异步 worker 使用提交时的文档内容。"}]}]
                }
                """);
    }

    private Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }
}
