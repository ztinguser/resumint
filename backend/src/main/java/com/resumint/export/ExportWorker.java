package com.resumint.export;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExportWorker {
    private static final Logger log = LoggerFactory.getLogger(ExportWorker.class);
    private final ExportJobRepository repository; private final PdfRenderer renderer; private final ExportLimits limits; private final Clock clock; private final ExportStorage storage;
    public ExportWorker(ExportJobRepository repository,PdfRenderer renderer,ExportLimits limits,Clock clock,ExportStorage storage) { this.repository=repository;this.renderer=renderer;this.limits=limits;this.clock=clock;this.storage=storage; }
    public boolean runOnce(String workerId) {
        cleanupExpired(); Instant now=Instant.now(clock); var candidate=repository.claimNext(workerId,now,limits.leaseDuration()); if(candidate.isEmpty()) return false;
        ExportJob job=candidate.get(); Path temporary=null; String resultRef=storage.resultRef(job.id());
        try {
            temporary=storage.temporaryResultPath(job.id()); Path result=storage.resultPath(resultRef);
            renderer.render(storage.readSnapshot(job.snapshotRef()),temporary); moveIntoPlace(temporary,result);
            repository.succeed(job.id(),workerId,resultRef,Instant.now(clock),Instant.now(clock).plus(limits.resultRetention()));
            ExportJob updated=repository.findById(job.id()).orElseThrow(); if(updated.status()==ExportStatus.CANCELLED) storage.deleteQuietly(resultRef);
        } catch(Exception exception) {
            log.error("PDF export {} failed", job.id(), exception);
            if(temporary!=null) try { Files.deleteIfExists(temporary); } catch(Exception ignored) { }
            repository.fail(job.id(),workerId,"PDF_RENDER_FAILED",Instant.now(clock));
        }
        cleanupExpired(); return true;
    }
    public void cleanupExpired() {
        Instant now=Instant.now(clock);
        for(ExportJob job:repository.expireAndFindCleanupCandidates(now)) { storage.deleteQuietly(job.snapshotRef()); storage.deleteQuietly(job.resultRef()); repository.clearStorageReferences(job.id(),now); }
    }
    private static void moveIntoPlace(Path source,Path target) throws Exception { try { Files.move(source,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); } catch(AtomicMoveNotSupportedException e) { Files.move(source,target,StandardCopyOption.REPLACE_EXISTING); } }
}
