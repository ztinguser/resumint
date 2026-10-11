package com.resumint.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class AdminBootstrap implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final String configuredPhone;

    AdminBootstrap(JdbcTemplate jdbc,@Value("${resumint.admin.phone:}") String configuredPhone){
        this.jdbc=jdbc;this.configuredPhone=configuredPhone;
    }

    @Override public void run(ApplicationArguments args){
        if(configuredPhone==null||configuredPhone.isBlank())return;
        String phone=PhoneNumbers.normalize(configuredPhone);
        jdbc.update("UPDATE users SET role='ADMIN',updated_at=CURRENT_TIMESTAMP(6) WHERE phone_e164=? AND role<>'ADMIN'",phone);
    }
}
