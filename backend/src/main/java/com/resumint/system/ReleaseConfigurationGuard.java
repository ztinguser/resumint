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
            @Value("${resumint.auth.phone-lookup-secret}") String phoneSecret,
            @Value("${resumint.export.credential-secret}") String exportSecret,
            @Value("${resumint.ops.token}") String opsToken,
            @Value("${resumint.auth.cookie-secure}") boolean secureCookies,
            @Value("${resumint.sms.provider}") String smsProvider,
            @Value("${resumint.export.storage-root}") Path storageRoot) {
        requireSecret("AUTH_PHONE_LOOKUP_SECRET", phoneSecret);
        requireSecret("EXPORT_CREDENTIAL_SECRET", exportSecret);
        requireSecret("RESUMINT_OPS_TOKEN", opsToken);
        if (!secureCookies) throw new IllegalStateException("release profile requires secure cookies");
        if (!"alibaba".equals(smsProvider)) throw new IllegalStateException("release profile requires the Alibaba SMS provider");
        if (!storageRoot.isAbsolute()) throw new IllegalStateException("release profile requires an absolute export storage root");
    }

    private static void requireSecret(String name, String value) {
        if (value == null || value.length() < 32 || List.of("dev-only", "change-me").stream().anyMatch(value::contains)) {
            throw new IllegalStateException(name + " must be a non-default secret containing at least 32 characters");
        }
    }
}
