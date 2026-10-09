package com.resumint.export;

import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
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
            @Value("${resumint.export.result-retention}") Duration resultRetention) {
        if (workerConcurrency != 1) throw new IllegalArgumentException("M1 worker concurrency must remain 1");
        return new ExportLimits(printBaseUrl, workerConcurrency, queueCapacity, maxSnapshotBytes,
                renderTimeout, leaseDuration, resultRetention);
    }

    @Bean Clock exportClock() { return Clock.systemUTC(); }
    @Bean ExportJobRepository exportJobRepository() { return new InMemoryExportJobRepository(); }
    @Bean ResumeDocumentSchema resumeDocumentSchema(ObjectMapper objectMapper) { return new ResumeDocumentSchema(objectMapper); }
    @Bean PdfRenderer pdfRenderer(ExportLimits limits) { return new PlaywrightPdfRenderer(limits); }
    @Bean ExportCoordinator exportCoordinator(ExportJobRepository repository, ResumeDocumentSchema schema,
            ExportLimits limits, ObjectMapper objectMapper, Clock clock) {
        return new ExportCoordinator(repository, schema, limits, objectMapper, clock);
    }
    @Bean ExportWorker exportWorker(ExportJobRepository repository, PdfRenderer renderer, ExportLimits limits, Clock clock) {
        Path output = Path.of(System.getProperty("java.io.tmpdir"), "resumint-pdf");
        return new ExportWorker(repository, renderer, limits, clock, output);
    }
}
