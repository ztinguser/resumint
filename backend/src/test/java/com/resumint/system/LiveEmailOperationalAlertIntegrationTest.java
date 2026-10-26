package com.resumint.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties={"resumint.alert.email.enabled=true","resumint.alert.email.poll-interval=1h","resumint.export.worker-enabled=false"})
@EnabledIfSystemProperty(named="resumint.live.email.it",matches="true")
class LiveEmailOperationalAlertIntegrationTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:33306/resumint?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username",()->"resumint");registry.add("spring.datasource.password",()->"resumint");
    }
    @Autowired OperationalAlertService alerts;
    @Autowired OperationalAlertNotificationRunner runner;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean(){jdbc.update("DELETE FROM operational_alerts");}

    @Test void sendsARealRedactedProbeThroughConfiguredSmtp(){
        Instant now=Instant.now();
        assertThat(alerts.create("EMAIL_DELIVERY_PROBE",now,1,1,now)).isTrue();
        runner.deliverOnce();
        assertThat(jdbc.queryForObject("SELECT notification_status FROM operational_alerts WHERE alert_type='EMAIL_DELIVERY_PROBE'",String.class)).isEqualTo("SENT");
    }
}
