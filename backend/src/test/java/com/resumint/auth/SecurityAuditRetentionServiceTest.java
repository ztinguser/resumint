package com.resumint.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SecurityAuditRetentionServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-26T12:00:00Z");

    @Test void removesOnlyRecordsStrictlyOlderThanConfiguredRetention() {
        SecurityAuditRepository repository = mock(SecurityAuditRepository.class);
        Instant cutoff = NOW.minus(Duration.ofDays(180));
        when(repository.deleteCreatedBefore(cutoff)).thenReturn(3);

        SecurityAuditRetentionService retention = new SecurityAuditRetentionService(
                repository, Clock.fixed(NOW, ZoneOffset.UTC), 180);

        assertThat(retention.cleanupOnce()).isEqualTo(3);
        verify(repository).deleteCreatedBefore(cutoff);
    }

    @Test void rejectsNonPositiveRetention() {
        assertThatThrownBy(() -> new SecurityAuditRetentionService(
                mock(SecurityAuditRepository.class), Clock.fixed(NOW, ZoneOffset.UTC), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }
}
