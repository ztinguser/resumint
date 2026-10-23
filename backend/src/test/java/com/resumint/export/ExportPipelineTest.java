package com.resumint.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.resumint.web.ApiException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ExportPipelineTest {
    private static final Instant NOW=Instant.parse("2026-01-01T00:00:00Z");
    private static final String SECRET="test-export-credential-secret-32-characters";
    private final ObjectMapper objectMapper=new ObjectMapper();

    @Test void sharedSchemaIdempotencyAndCredentialAuthorization(@TempDir java.nio.file.Path directory) throws Exception {
        var repository=new InMemoryExportJobRepository(); var storage=new ExportStorage(directory,objectMapper); var coordinator=coordinator(repository,storage,limits());
        var first=coordinator.submit("stable-key-01",new ExportSnapshot(validDocument())); var repeated=coordinator.submit("stable-key-01",new ExportSnapshot(validDocument()));
        assertThat(repeated.job().id()).isEqualTo(first.job().id()); assertThat(repeated.credential()).isEqualTo(first.credential()); assertThat(repository.activeCount()).isEqualTo(1);
        assertThat(coordinator.authorized(first.job().id(),first.credential()).id()).isEqualTo(first.job().id());
        assertThatThrownBy(()->coordinator.authorized(first.job().id(),"wrong-token")).isInstanceOf(ApiException.class).hasMessageContaining("凭据");
    }

    @Test void expiredLeaseCanBeRecoveredByAnotherWorker(@TempDir java.nio.file.Path directory) throws Exception {
        var repository=new InMemoryExportJobRepository();var storage=new ExportStorage(directory,objectMapper);var coordinator=coordinator(repository,storage,limits());
        ExportJob job=coordinator.submit("stable-key-02",new ExportSnapshot(validDocument())).job();
        assertThat(repository.claimNext("worker-a",NOW,Duration.ofSeconds(60))).isPresent();assertThat(repository.claimNext("worker-b",NOW.plusSeconds(59),Duration.ofSeconds(60))).isEmpty();assertThat(repository.claimNext("worker-b",NOW.plusSeconds(61),Duration.ofSeconds(60))).isPresent();assertThat(repository.findById(job.id()).orElseThrow().leaseOwner()).isEqualTo("worker-b");
    }

    @Test void workerPublishesFinalFileAndCleanupRemovesPrivateResources(@TempDir java.nio.file.Path directory) throws Exception {
        var repository=new InMemoryExportJobRepository();var storage=new ExportStorage(directory,objectMapper);var coordinator=coordinator(repository,storage,limits());
        ExportJob submitted=coordinator.submit("stable-key-03",new ExportSnapshot(validDocument())).job();
        PdfRenderer renderer=(snapshot,output)->Files.writeString(output,"%PDF-test",StandardCharsets.US_ASCII);var worker=new ExportWorker(repository,renderer,limits(),fixedClock(),storage);
        assertThat(worker.runOnce("worker-test")).isTrue();ExportJob completed=repository.findById(submitted.id()).orElseThrow();assertThat(completed.status()).isEqualTo(ExportStatus.SUCCEEDED);assertThat(storage.exists(completed.resultRef())).isTrue();
        var futureClock=Clock.fixed(NOW.plus(Duration.ofMinutes(16)),ZoneOffset.UTC);new ExportWorker(repository,renderer,limits(),futureClock,storage).cleanupExpired();ExportJob expired=repository.findById(submitted.id()).orElseThrow();assertThat(expired.status()).isEqualTo(ExportStatus.EXPIRED);assertThat(expired.snapshotRef()).isNull();assertThat(expired.resultRef()).isNull();
    }

    @Test void cancellationIsIdempotentAndDoesNotExposeAResult(@TempDir java.nio.file.Path directory) throws Exception {
        var repository=new InMemoryExportJobRepository();var storage=new ExportStorage(directory,objectMapper);var coordinator=coordinator(repository,storage,limits());var submitted=coordinator.submit("stable-key-06",new ExportSnapshot(validDocument()));
        assertThat(coordinator.cancel(submitted.job().id(),submitted.credential()).status()).isEqualTo(ExportStatus.CANCELLED);assertThat(coordinator.cancel(submitted.job().id(),submitted.credential()).status()).isEqualTo(ExportStatus.CANCELLED);
    }

    @Test void queuedJobAndSnapshotExpireWithGuestCredential(@TempDir java.nio.file.Path directory) throws Exception {
        var repository=new InMemoryExportJobRepository();var storage=new ExportStorage(directory,objectMapper);var coordinator=coordinator(repository,storage,limits());var submitted=coordinator.submit("stable-key-07",new ExportSnapshot(validDocument()));
        assertThat(storage.exists(submitted.job().snapshotRef())).isTrue();
        var futureClock=Clock.fixed(NOW.plus(Duration.ofMinutes(21)),ZoneOffset.UTC);new ExportWorker(repository,(snapshot,output)->{},limits(),futureClock,storage).cleanupExpired();
        ExportJob expired=repository.findById(submitted.job().id()).orElseThrow();assertThat(expired.status()).isEqualTo(ExportStatus.EXPIRED);assertThat(expired.snapshotRef()).isNull();assertThat(repository.activeCount()).isZero();
    }

    @Test void rejectsInvalidSchemaOversizedSnapshotsAndFullQueue(@TempDir java.nio.file.Path directory) throws Exception {
        var repository=new InMemoryExportJobRepository();var storage=new ExportStorage(directory,objectMapper);var tiny=new ExportLimits(URI.create("http://127.0.0.1:3100"),1,1,8,Duration.ofSeconds(45),Duration.ofSeconds(60),Duration.ofMinutes(15),Duration.ofMinutes(20),Duration.ofMinutes(30));var coordinator=coordinator(repository,storage,tiny);
        assertThatThrownBy(()->coordinator.submit("stable-key-04",new ExportSnapshot(objectMapper.readTree("{}")))).isInstanceOf(ApiException.class).hasMessageContaining("missing");
        assertThatThrownBy(()->coordinator.submit("stable-key-05",new ExportSnapshot(validDocument()))).isInstanceOf(ApiException.class).hasMessageContaining("限制");
    }

    @Test void compensationCleanupRemovesOnlyOldUnreferencedFiles(@TempDir java.nio.file.Path directory) throws Exception {
        var repository=new InMemoryExportJobRepository();var storage=new ExportStorage(directory,objectMapper);
        var coordinator=coordinator(repository,storage,limits());var submitted=coordinator.submit("stable-key-08",new ExportSnapshot(validDocument()));
        var orphan=directory.resolve("results/orphan.pdf");Files.createDirectories(orphan.getParent());Files.writeString(orphan,"orphan");
        Files.setLastModifiedTime(orphan,FileTime.from(NOW.minus(Duration.ofHours(1))));
        var recent=directory.resolve("results/recent.pdf");Files.writeString(recent,"recent");
        Files.setLastModifiedTime(recent,FileTime.from(NOW.minus(Duration.ofMinutes(5))));
        new ExportWorker(repository,(snapshot,output)->{},limits(),fixedClock(),storage).cleanupExpired();
        assertThat(orphan).doesNotExist();assertThat(recent).exists();assertThat(storage.exists(submitted.job().snapshotRef())).isTrue();
    }

    @Test void rotatedCredentialKeyRingKeepsExistingJobsReachable() {
        var jobId=java.util.UUID.randomUUID();
        var oldKeys=new ExportCredentialService("old-export-credential-secret-32-characters");
        String oldCredential=oldKeys.issue(jobId);String digest=oldKeys.digest(oldCredential);
        var rotated=new ExportCredentialService("new-export-credential-secret-32-characters,old-export-credential-secret-32-characters");
        assertThat(rotated.issue(jobId,digest)).isEqualTo(oldCredential);
        assertThat(rotated.issue(jobId)).isNotEqualTo(oldCredential);
        assertThatThrownBy(()->new ExportCredentialService("new-export-credential-secret-32-characters").issue(jobId,digest))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no longer present");
    }

    private ExportCoordinator coordinator(ExportJobRepository repository,ExportStorage storage,ExportLimits limits){return new ExportCoordinator(repository,new ResumeDocumentSchema(objectMapper),limits,objectMapper,fixedClock(),storage,new ExportCredentialService(SECRET));}
    private ExportLimits limits(){return ExportLimits.m1Defaults(URI.create("http://127.0.0.1:3100"));}
    private JsonNode validDocument() throws Exception{return objectMapper.readTree("""
      {"schemaVersion":"1.0","documentId":"test-document","template":{"id":"benchmark-template","version":1},"locale":"zh-CN","profile":{"fullName":"林知夏","headline":"异步导出验证","email":"test@example.com","website":{"label":"作品集","href":"https://example.com/portfolio"}},"layout":{"kind":"single","page":{"size":"A4","marginMm":16},"theme":{"fontFamily":"Arial","accentColor":"#244b45"},"decorations":[]},"sections":[{"id":"test-section","type":"custom","title":"Java Playwright PDF","column":"main","visible":true,"items":[{"id":"test-item","title":"不可变快照","summary":"异步 worker 使用提交时的文档内容。"}]}]}
      """);}
    private Clock fixedClock(){return Clock.fixed(NOW,ZoneOffset.UTC);}
}
