package com.resumint.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ReleaseLoggingConfigurationTest {
    @Test void releaseSeparatesApiAndWorkerLogsAndKeepsThirtyDays() throws IOException {
        String release = resource("/application-release.yml");
        String api = resource("/application-api.yml");
        String worker = resource("/application-worker.yml");

        assertThat(release)
                .contains("name: ${APPLICATION_LOG_DIRECTORY}/resumint-${resumint.runtime-role}.log")
                .contains("max-history: ${APPLICATION_LOG_RETENTION_DAYS}")
                .contains("clean-history-on-start: true");
        assertThat(api).contains("runtime-role: api");
        assertThat(worker).contains("runtime-role: worker");
    }

    private static String resource(String name) throws IOException {
        try (var input = ReleaseLoggingConfigurationTest.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("missing resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
