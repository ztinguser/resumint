package com.resumint.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailOperationalAlertNotifierTest {
    @Test void sendsPlainTextRedactedCostAlertToConfiguredRecipients(){
        JavaMailSender sender=mock(JavaMailSender.class);
        var notifier=new EmailOperationalAlertNotifier(sender,"alerts@163.com","owner@example.com, backup@example.com");
        UUID id=UUID.randomUUID();
        notifier.send(new OperationalAlert(id,"SMS_DAILY_COST",Instant.parse("2026-10-26T00:00:00Z"),50,50,Instant.parse("2026-10-26T01:00:00Z"),1));
        ArgumentCaptor<SimpleMailMessage> message=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("alerts@163.com");
        assertThat(message.getValue().getTo()).containsExactly("owner@example.com","backup@example.com");
        assertThat(message.getValue().getSubject()).contains("短信日发送量达到费用阈值");
        assertThat(message.getValue().getText()).contains(id.toString(),"当前值：50","阈值：50")
                .doesNotContain("13800138000","password=","secret=");
    }
}
