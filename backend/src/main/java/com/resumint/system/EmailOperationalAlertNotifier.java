package com.resumint.system;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="resumint.alert.email.enabled",havingValue="true")
final class EmailOperationalAlertNotifier implements OperationalAlertNotifier {
    private final JavaMailSender sender;
    private final String from;
    private final String[] recipients;

    EmailOperationalAlertNotifier(JavaMailSender sender,
            @Value("${resumint.alert.email.from}") String from,
            @Value("${resumint.alert.email.to}") String to) {
        this.sender=sender;this.from=requiredAddress("ALERT_EMAIL_FROM",from);
        List<String> values=Arrays.stream(to.split(",")).map(String::trim).filter(value->!value.isEmpty()).toList();
        if(values.isEmpty())throw new IllegalStateException("ALERT_EMAIL_TO is required when email alerts are enabled");
        this.recipients=values.stream().map(value->requiredAddress("ALERT_EMAIL_TO",value)).toArray(String[]::new);
    }

    @Override public void send(OperationalAlert alert) {
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(from);message.setTo(recipients);
        message.setSubject("[Resumint][严重] "+title(alert.type()));
        message.setText("""
                Resumint 触发了需要处理的运维告警。

                类型：%s
                时间窗口（UTC）：%s
                当前值：%d
                阈值：%d
                告警 ID：%s

                此邮件不包含手机号、验证码、简历内容或密钥。请登录服务器查看受保护的运维状态。
                """.formatted(alert.type(),alert.windowStart(),alert.observedValue(),alert.thresholdValue(),alert.id()));
        sender.send(message);
    }

    private static String title(String type){return "SMS_DAILY_COST".equals(type)?"短信日发送量达到费用阈值":"运维告警 "+type;}
    private static String requiredAddress(String name,String value){
        String normalized=value==null?"":value.trim();
        if(normalized.isEmpty()||normalized.contains("\r")||normalized.contains("\n")||!normalized.contains("@"))
            throw new IllegalStateException(name+" must contain a valid email address");
        return normalized;
    }
}
