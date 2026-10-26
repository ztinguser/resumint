package com.resumint.system;

import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public final class OperationalAlertService {
    private final OperationalAlertRepository repository;

    OperationalAlertService(OperationalAlertRepository repository) { this.repository=repository; }

    public boolean create(String type, Instant windowStart, long observed, long threshold, Instant now) {
        return repository.create(type,windowStart,observed,threshold,now);
    }
}
