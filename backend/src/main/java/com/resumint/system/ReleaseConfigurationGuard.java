package com.resumint.system;

import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("release")
final class ReleaseConfigurationGuard {
    ReleaseConfigurationGuard(
            @Value("${resumint.auth.phone-lookup-secrets}") String phoneSecrets,
            @Value("${resumint.export.credential-secrets}") String exportSecrets,
            @Value("${resumint.ops.token}") String opsToken,
            @Value("${resumint.auth.cookie-secure}") boolean secureCookies,
            @Value("${resumint.sms.provider}") String smsProvider,
            @Value("${resumint.alert.email.enabled}") boolean emailAlertsEnabled,
            @Value("${spring.mail.host}") String smtpHost,
            @Value("${spring.mail.username}") String smtpUsername,
            @Value("${spring.mail.password}") String smtpPassword,
            @Value("${resumint.alert.email.from}") String emailFrom,
            @Value("${resumint.alert.email.to}") String emailTo,
            @Value("${resumint.export.storage-root}") Path storageRoot) {
        requireKeyRing("AUTH_PHONE_LOOKUP_SECRETS", phoneSecrets);
        requireKeyRing("EXPORT_CREDENTIAL_SECRETS", exportSecrets);
        requireSecret("RESUMINT_OPS_TOKEN", opsToken);
        if (!secureCookies) throw new IllegalStateException("release profile requires secure cookies");
        if (!"alibaba".equals(smsProvider)) throw new IllegalStateException("release profile requires the Alibaba SMS provider");
        if (!emailAlertsEnabled) throw new IllegalStateException("release profile requires email alerts");
        requirePresent("ALERT_EMAIL_SMTP_HOST",smtpHost);
        requirePresent("ALERT_EMAIL_USERNAME",smtpUsername);
        requirePresent("ALERT_EMAIL_AUTHORIZATION_CODE",smtpPassword);
        requirePresent("ALERT_EMAIL_FROM",emailFrom);
        requirePresent("ALERT_EMAIL_TO",emailTo);
        if (!storageRoot.isAbsolute()) throw new IllegalStateException("release profile requires an absolute export storage root");
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.length() < 32 || List.of("dev-only", "change-me").stream().anyMatch(value::contains)) {
            throw new IllegalStateException(name + " must be a non-default secret containing at least 32 characters");
        }
    }
    private static void requireKeyRing(String name,String value){
        if(value==null||value.isBlank())throw new IllegalStateException(name+" is required");
        for(String secret:value.split(","))requireSecret(name,secret.trim());
    }
    private static void requirePresent(String name,String value){
        if(value==null||value.isBlank()||value.contains("<"))throw new IllegalStateException(name+" is required");
    }
}
