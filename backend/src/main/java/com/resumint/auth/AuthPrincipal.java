package com.resumint.auth;

import java.security.Principal;
import java.util.UUID;

public record AuthPrincipal(UUID userId, UUID sessionId, String maskedPhone) implements Principal {
    @Override public String getName() { return userId.toString(); }
}
