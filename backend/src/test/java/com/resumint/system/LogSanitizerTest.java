package com.resumint.system;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class LogSanitizerTest {
    @Test void removesPhonesSecretsAndEmbeddedImages() {
        String value="phone=13800138000 Authorization: Bearer-secret api_key=sk-test data:image/png;base64,AAAA";
        String safe=LogSanitizer.sanitize(value);
        assertThat(safe).doesNotContain("13800138000","Bearer-secret","sk-test","AAAA")
                .contains("[phone-redacted]","[redacted]","[image-data-redacted]");
    }
}
