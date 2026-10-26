package com.resumint.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
final class SecurityAuditRetentionService {
    private final SecurityAuditRepository repository;
    private final Clock clock;
    private final int retentionDays;

    SecurityAuditRetentionService(SecurityAuditRepository repository, Clock clock,
            @Value("${resumint.security.audit-retention-days}") int retentionDays) {
        if (retentionDays < 1) throw new IllegalArgumentException("security audit retention days must be positive");
        this.repository = repository;
        this.clock = clock;
        this.retentionDays = retentionDays;
    }

    int cleanupOnce() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(retentionDays));
        return repository.deleteCreatedBefore(cutoff);
    }
}
