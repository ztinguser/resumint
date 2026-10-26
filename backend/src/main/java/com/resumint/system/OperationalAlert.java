package com.resumint.system;

import java.time.Instant;
import java.util.UUID;

public record OperationalAlert(UUID id, String type, Instant windowStart, long observedValue,
        long thresholdValue, Instant createdAt, int notificationAttempts) { }
