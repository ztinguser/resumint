package com.resumint.export;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ExportCredentialService {
    private final List<byte[]> secrets;
    public ExportCredentialService(String keyRing) {
        if(keyRing==null)throw new IllegalArgumentException("export credential key ring is required");
        this.secrets=Arrays.stream(keyRing.split(",")).map(String::trim).filter(value->!value.isEmpty()).map(value->{
            if(value.length()<32)throw new IllegalArgumentException("export credential key ring contains a secret shorter than 32 characters");
            return value.getBytes(StandardCharsets.UTF_8);
        }).toList();
        if(secrets.isEmpty())throw new IllegalArgumentException("export credential key ring is empty");
    }
    public String issue(UUID jobId) {
        return issue(jobId,secrets.getFirst());
    }
    public String issue(UUID jobId,String expectedDigest){
        for(byte[] secret:secrets){String credential=issue(jobId,secret);if(matches(credential,expectedDigest))return credential;}
        throw new IllegalStateException("the key required by this export job is no longer present in the configured key ring");
    }
    private String issue(UUID jobId,byte[] secret) {
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
