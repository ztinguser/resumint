package com.resumint.auth;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("api")
final class SecurityAuditRetentionRunner {
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditRetentionRunner.class);
    private final SecurityAuditRetentionService retention;
    private ScheduledExecutorService executor;

    SecurityAuditRetentionRunner(SecurityAuditRetentionService retention) { this.retention = retention; }

    @PostConstruct void start() {
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "security-audit-retention");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::cleanupSafely, 1, 24, TimeUnit.HOURS);
    }

    @PreDestroy void stop() { if (executor != null) executor.shutdownNow(); }

    void cleanupOnce() {
        int deleted = retention.cleanupOnce();
        if (deleted > 0) log.info("SECURITY_AUDIT_RETENTION_CLEANUP deleted={}", deleted);
    }

    private void cleanupSafely() {
        try { cleanupOnce(); }
        catch (RuntimeException exception) {
            log.warn("SECURITY_AUDIT_RETENTION_CLEANUP_FAILED errorClass={}", exception.getClass().getSimpleName());
        }
    }
}
