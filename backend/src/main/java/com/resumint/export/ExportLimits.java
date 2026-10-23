package com.resumint.export;

import java.net.URI;
import java.time.Duration;

public record ExportLimits(
        URI printBaseUrl,
        int workerConcurrency,
        int queueCapacity,
        int maxSnapshotBytes,
        Duration renderTimeout,
        Duration leaseDuration,
        Duration resultRetention,
        Duration credentialTtl,
        Duration orphanGrace) {

    public static ExportLimits m1Defaults(URI printBaseUrl) {
        return new ExportLimits(printBaseUrl, 1, 20, 524_288,
                Duration.ofSeconds(45), Duration.ofSeconds(60), Duration.ofMinutes(15), Duration.ofMinutes(20), Duration.ofMinutes(30));
    }
}
