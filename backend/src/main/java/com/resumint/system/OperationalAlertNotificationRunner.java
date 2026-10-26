package com.resumint.system;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="resumint.alert.email.enabled",havingValue="true")
final class OperationalAlertNotificationRunner {
    private static final Logger log=LoggerFactory.getLogger(OperationalAlertNotificationRunner.class);
    private final OperationalAlertRepository repository;
    private final OperationalAlertNotifier notifier;
    private final Clock clock;
    private final Duration interval;
    private final Duration retryDelay;
    private final Duration lease;
    private final int maxAttempts;
    private ScheduledExecutorService executor;

    OperationalAlertNotificationRunner(OperationalAlertRepository repository,OperationalAlertNotifier notifier,Clock clock,
            @Value("${resumint.alert.email.poll-interval}") Duration interval,
            @Value("${resumint.alert.email.retry-delay}") Duration retryDelay,
            @Value("${resumint.alert.email.delivery-lease}") Duration lease,
            @Value("${resumint.alert.email.max-attempts}") int maxAttempts){
        if(interval.isNegative()||interval.isZero()||retryDelay.isNegative()||retryDelay.isZero()||lease.isNegative()||lease.isZero()||maxAttempts<1)
            throw new IllegalArgumentException("email alert delivery limits must be positive");
        this.repository=repository;this.notifier=notifier;this.clock=clock;this.interval=interval;
        this.retryDelay=retryDelay;this.lease=lease;this.maxAttempts=maxAttempts;
    }

    @PostConstruct void start(){
        executor=Executors.newSingleThreadScheduledExecutor(runnable->{Thread thread=new Thread(runnable,"operational-alert-email");thread.setDaemon(true);return thread;});
        executor.scheduleWithFixedDelay(this::deliverSafely,Math.min(5,interval.toSeconds()),Math.max(1,interval.toSeconds()),TimeUnit.SECONDS);
    }
    @PreDestroy void stop(){if(executor!=null)executor.shutdownNow();}

    void deliverOnce(){
        Instant now=clock.instant();
        repository.claimNext(now,lease,maxAttempts).ifPresent(alert->{
            try{
                notifier.send(alert);repository.sent(alert.id(),clock.instant());
                log.info("OPERATIONAL_ALERT_EMAIL_SENT type={} alertId={} attempt={}",alert.type(),alert.id(),alert.notificationAttempts());
            }catch(RuntimeException exception){
                repository.failed(alert.id(),alert.notificationAttempts(),maxAttempts,clock.instant().plus(retryDelay),"SMTP_SEND_FAILED");
                log.warn("OPERATIONAL_ALERT_EMAIL_FAILED type={} alertId={} attempt={} errorClass={}",alert.type(),alert.id(),alert.notificationAttempts(),exception.getClass().getSimpleName());
            }
        });
    }
    private void deliverSafely(){try{deliverOnce();}catch(RuntimeException exception){log.warn("OPERATIONAL_ALERT_EMAIL_RUNNER_FAILED errorClass={}",exception.getClass().getSimpleName());}}
}
