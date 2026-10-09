package com.resumint.export;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "resumint.export.worker-enabled", havingValue = "true")
public final class ExportWorkerRunner implements SmartLifecycle {
    private static final Logger log=LoggerFactory.getLogger(ExportWorkerRunner.class);
    private final ExportWorker worker;
    private final String workerId = "worker-" + UUID.randomUUID();
    private ScheduledExecutorService executor;

    public ExportWorkerRunner(ExportWorker worker) {
        this.worker = worker;
    }

    @Override
    public synchronized void start() {
        if (isRunning()) return;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "resumint-pdf-worker");
            thread.setDaemon(false);
            return thread;
        });
        executor.scheduleWithFixedDelay(() -> {
            try { worker.runOnce(workerId); }
            catch (Exception exception) { log.error("Export worker iteration failed; the queue will retry",exception); }
        }, 0, Duration.ofMillis(250).toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public synchronized void stop() {
        if (executor != null) executor.shutdownNow();
        executor = null;
    }

    @Override public synchronized boolean isRunning() { return executor != null && !executor.isShutdown(); }
}
