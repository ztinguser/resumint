package com.resumint.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name="resumint.sms.provider",havingValue="local-test")
public class LocalSmsTestConfiguration {
    @Bean SmsProvider localSmsTestProvider(){
        return new SmsProvider(){
            @Override public String name(){return "local-test";}
            @Override public SendResult send(String phoneNational,String outId,String purpose){return new SendResult("local-test-"+outId);}
            @Override public boolean verify(String phoneNational,String outId,String code){return "123456".equals(code);}
        };
    }
}
