package com.resumint.system;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalAlertNotificationRunnerTest {
    private static final Instant NOW=Instant.parse("2026-10-26T01:00:00Z");
    private static final Duration RETRY=Duration.ofMinutes(5);

    @Test void marksClaimedAlertSent(){
        OperationalAlertRepository repository=mock(OperationalAlertRepository.class);
        OperationalAlertNotifier notifier=mock(OperationalAlertNotifier.class);
        OperationalAlert alert=alert(1);
        when(repository.claimNext(NOW,Duration.ofMinutes(2),3)).thenReturn(Optional.of(alert));
        runner(repository,notifier).deliverOnce();
        verify(notifier).send(alert);verify(repository).sent(alert.id(),NOW);
    }

    @Test void retriesWithoutPersistingExceptionDetails(){
        OperationalAlertRepository repository=mock(OperationalAlertRepository.class);
        OperationalAlertNotifier notifier=mock(OperationalAlertNotifier.class);
        OperationalAlert alert=alert(1);
        when(repository.claimNext(NOW,Duration.ofMinutes(2),3)).thenReturn(Optional.of(alert));
        doThrow(new IllegalStateException("password=must-not-be-stored")).when(notifier).send(alert);
        runner(repository,notifier).deliverOnce();
        verify(repository).failed(eq(alert.id()),eq(1),eq(3),eq(NOW.plus(RETRY)),eq("SMTP_SEND_FAILED"));
    }

    private static OperationalAlertNotificationRunner runner(OperationalAlertRepository repository,OperationalAlertNotifier notifier){
        return new OperationalAlertNotificationRunner(repository,notifier,Clock.fixed(NOW,ZoneOffset.UTC),Duration.ofSeconds(30),RETRY,Duration.ofMinutes(2),3);
    }
    private static OperationalAlert alert(int attempts){return new OperationalAlert(UUID.randomUUID(),"SMS_DAILY_COST",NOW.minus(Duration.ofHours(1)),50,50,NOW,attempts);}
}
