package com.resumint.export;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ExportCredentialService {
    private final byte[] secret;
    public ExportCredentialService(String secret) {
        if (secret == null || secret.length() < 32) throw new IllegalArgumentException("export credential secret must contain at least 32 characters");
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }
    public String issue(UUID jobId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(jobId.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("cannot issue export credential", exception); }
    }
    public String digest(String credential) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(credential.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("cannot digest export credential", exception); }
    }
    public boolean matches(String credential, String expectedDigest) {
        if (credential == null || expectedDigest == null) return false;
        return MessageDigest.isEqual(digest(credential).getBytes(StandardCharsets.US_ASCII), expectedDigest.getBytes(StandardCharsets.US_ASCII));
    }
}
