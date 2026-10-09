package com.resumint.export;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;

public final class ExportWorker {
    private final ExportJobRepository repository;
    private final PdfRenderer renderer;
    private final ExportLimits limits;
    private final Clock clock;
    private final Path outputDirectory;

    public ExportWorker(ExportJobRepository repository, PdfRenderer renderer, ExportLimits limits, Clock clock, Path outputDirectory) {
        this.repository = repository;
        this.renderer = renderer;
        this.limits = limits;
        this.clock = clock;
        this.outputDirectory = outputDirectory;
    }

    public boolean runOnce(String workerId) {
        Instant now = Instant.now(clock);
        var candidate = repository.claimNext(workerId, now, limits.leaseDuration());
        if (candidate.isEmpty()) return false;

        ExportJob job = candidate.get();
        Path temporary = outputDirectory.resolve(job.id() + ".pdf.tmp");
        Path result = outputDirectory.resolve(job.id() + ".pdf");
        try {
            Files.createDirectories(outputDirectory);
            renderer.render(job.snapshot(), temporary);
            moveIntoPlace(temporary, result);
            job.complete(workerId, result, Instant.now(clock).plus(limits.resultRetention()));
        } catch (Exception exception) {
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            job.fail(workerId, "PDF_RENDER_FAILED");
        }
        return true;
    }

    private static void moveIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
