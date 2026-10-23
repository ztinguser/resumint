package com.resumint.export;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ExportConfiguration {
    @Bean
    ExportLimits exportLimits(
            @Value("${resumint.export.print-base-url}") URI printBaseUrl,
            @Value("${resumint.export.worker-concurrency}") int workerConcurrency,
            @Value("${resumint.export.queue-capacity}") int queueCapacity,
            @Value("${resumint.export.max-snapshot-bytes}") int maxSnapshotBytes,
            @Value("${resumint.export.render-timeout}") Duration renderTimeout,
            @Value("${resumint.export.lease-duration}") Duration leaseDuration,
            @Value("${resumint.export.result-retention}") Duration resultRetention,
            @Value("${resumint.export.credential-ttl}") Duration credentialTtl,
            @Value("${resumint.export.orphan-grace}") Duration orphanGrace) {
        if (workerConcurrency != 1) throw new IllegalArgumentException("initial worker concurrency must remain 1");
        return new ExportLimits(printBaseUrl, workerConcurrency, queueCapacity, maxSnapshotBytes,
                renderTimeout, leaseDuration, resultRetention, credentialTtl, orphanGrace);
    }

    @Bean Clock exportClock() { return Clock.systemUTC(); }
    @Bean @ConditionalOnProperty(name="resumint.export.repository",havingValue="memory")
    ExportJobRepository inMemoryExportJobRepository() { return new InMemoryExportJobRepository(); }
    @Bean @ConditionalOnProperty(name="resumint.export.repository",havingValue="mysql",matchIfMissing=true)
    ExportJobRepository myBatisExportJobRepository(ExportJobMapper mapper) { return new MyBatisExportJobRepository(mapper); }
    @Bean ResumeDocumentSchema resumeDocumentSchema(ObjectMapper objectMapper) { return new ResumeDocumentSchema(objectMapper); }
    @Bean PdfRenderer pdfRenderer(ExportLimits limits) { return new PlaywrightPdfRenderer(limits); }
    @Bean ExportStorage exportStorage(@Value("${resumint.export.storage-root}") Path root,ObjectMapper objectMapper) { return new ExportStorage(root,objectMapper); }
    @Bean ExportCredentialService exportCredentialService(@Value("${resumint.export.credential-secrets}") String secrets) { return new ExportCredentialService(secrets); }
    @Bean ExportCoordinator exportCoordinator(ExportJobRepository repository, ResumeDocumentSchema schema,
            ExportLimits limits, ObjectMapper objectMapper, Clock clock,ExportStorage storage,ExportCredentialService credentials) {
        return new ExportCoordinator(repository, schema, limits, objectMapper, clock,storage,credentials);
    }
    @Bean ExportWorker exportWorker(ExportJobRepository repository, PdfRenderer renderer, ExportLimits limits, Clock clock,ExportStorage storage) {
        return new ExportWorker(repository, renderer, limits, clock, storage);
    }
}
