package com.resumint.auth;

import com.resumint.web.ApiException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="resumint.sms.provider", havingValue="disabled", matchIfMissing=true)
final class DisabledSmsProvider implements SmsProvider {
    @Override public String name() { return "disabled"; }
    @Override public SendResult send(String phoneNational, String outId) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "SMS_NOT_CONFIGURED", "短信服务尚未安全配置");
    }
    @Override public boolean verify(String phoneNational, String outId, String code) { return false; }
}
