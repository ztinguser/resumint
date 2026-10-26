package com.resumint.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(properties="resumint.export.worker-enabled=false")
@EnabledIfSystemProperty(named="resumint.mysql.it",matches="true")
class OperationalAlertRepositoryIntegrationTest {
    private static final Instant CREATED=Instant.parse("2026-10-26T01:00:00Z");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry){
        registry.add("spring.datasource.url",()->"jdbc:mysql://127.0.0.1:33306/resumint?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC");
        registry.add("spring.datasource.username",()->"resumint");registry.add("spring.datasource.password",()->"resumint");
    }
    @Autowired OperationalAlertRepository repository;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void clean(){jdbc.update("DELETE FROM operational_alerts");}

    @Test void persistsClaimsRetriesAndCompletesDelivery(){
        assertThat(repository.create("SMS_DAILY_COST",CREATED.minus(Duration.ofHours(1)),50,50,CREATED)).isTrue();
        assertThat(repository.create("SMS_DAILY_COST",CREATED.minus(Duration.ofHours(1)),51,50,CREATED)).isFalse();
        OperationalAlert first=repository.claimNext(CREATED,Duration.ofMinutes(2),3).orElseThrow();
        assertThat(first.notificationAttempts()).isOne();
        assertThat(status(first.id())).isEqualTo("SENDING");
        repository.failed(first.id(),first.notificationAttempts(),3,CREATED.plus(Duration.ofMinutes(5)),"SMTP_SEND_FAILED");
        assertThat(status(first.id())).isEqualTo("RETRY");
        assertThat(repository.claimNext(CREATED.plus(Duration.ofMinutes(4)),Duration.ofMinutes(2),3)).isEmpty();
        OperationalAlert second=repository.claimNext(CREATED.plus(Duration.ofMinutes(5)),Duration.ofMinutes(2),3).orElseThrow();
        assertThat(second.notificationAttempts()).isEqualTo(2);
        repository.sent(second.id(),CREATED.plus(Duration.ofMinutes(5)));
        assertThat(status(second.id())).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT notification_error_code FROM operational_alerts WHERE id=?",String.class,second.id().toString())).isNull();
    }
    private String status(java.util.UUID id){return jdbc.queryForObject("SELECT notification_status FROM operational_alerts WHERE id=?",String.class,id.toString());}
}
