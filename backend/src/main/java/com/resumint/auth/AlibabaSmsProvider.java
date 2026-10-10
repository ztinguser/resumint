package com.resumint.auth;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.resumint.web.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="resumint.sms.provider", havingValue="alibaba")
final class AlibabaSmsProvider implements SmsProvider {
    private final Client client;
    private final String signName;
    private final java.util.Map<String, String> templateCodes;
    private final String schemeName;
    private final long validSeconds;

    AlibabaSmsProvider(
            @Value("${resumint.sms.endpoint}") String endpoint,
            @Value("${resumint.sms.sign-name}") String signName,
            @Value("${resumint.sms.login-template-code}") String loginTemplateCode,
            @Value("${resumint.sms.password-reset-template-code}") String passwordResetTemplateCode,
            @Value("${resumint.sms.phone-change-old-template-code}") String phoneChangeOldTemplateCode,
            @Value("${resumint.sms.phone-change-new-template-code}") String phoneChangeNewTemplateCode,
            @Value("${resumint.sms.scheme-name}") String schemeName,
            @Value("${resumint.auth.phone-lookup-secret}") String phoneLookupSecret,
            @Value("${resumint.sms.code-minutes}") long codeMinutes) throws Exception {
        String accessKeyId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        if (blank(accessKeyId) || blank(accessKeySecret) || blank(signName)
                || blank(loginTemplateCode) || blank(passwordResetTemplateCode)
                || blank(phoneChangeOldTemplateCode) || blank(phoneChangeNewTemplateCode)) {
            throw new IllegalStateException("Alibaba SMS requires environment credentials, sign name and all purpose-specific template codes");
        }
        if (phoneLookupSecret.startsWith("dev-only-")) {
            throw new IllegalStateException("Alibaba SMS requires a non-default phone lookup secret");
        }
        Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret).setEndpoint(endpoint);
        this.client = new Client(config);
        this.signName = signName;
        this.templateCodes = java.util.Map.of(
                "LOGIN", loginTemplateCode,
                "PASSWORD_RESET", passwordResetTemplateCode,
                "PHONE_CHANGE_OLD", phoneChangeOldTemplateCode,
                "PHONE_CHANGE_NEW", phoneChangeNewTemplateCode);
        this.schemeName = schemeName;
        this.validSeconds = codeMinutes * 60;
    }

    @Override public String name() { return "alibaba"; }

    @Override public SendResult send(String phoneNational, String outId, String purpose) {
        try {
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phoneNational).setCountryCode("86").setSignName(signName)
                    .setTemplateCode(templateCodes.get(purpose)).setTemplateParam("{\"code\":\"##code##\",\"min\":\"" + validSeconds / 60 + "\"}")
                    .setOutId(outId)
                    .setCodeLength(6L).setCodeType(1L).setDuplicatePolicy(1L)
                    .setValidTime(validSeconds).setReturnVerifyCode(false);
            if (!blank(schemeName)) request.setSchemeName(schemeName);
            var body = client.sendSmsVerifyCode(request).getBody();
            if (body == null || !Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "SMS_SEND_FAILED", "短信供应商未接受发送请求");
            }
            return new SendResult(body.getRequestId());
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SMS_PROVIDER_UNAVAILABLE", "短信供应商暂时不可用");
        }
    }

    @Override public boolean verify(String phoneNational, String outId, String code) {
        try {
            CheckSmsVerifyCodeRequest request = new CheckSmsVerifyCodeRequest()
                    .setPhoneNumber(phoneNational).setCountryCode("86").setOutId(outId).setVerifyCode(code);
            if (!blank(schemeName)) request.setSchemeName(schemeName);
            var body = client.checkSmsVerifyCode(request).getBody();
            if (body == null) throw providerUnavailable();
            if (!Boolean.TRUE.equals(body.getSuccess()) || !"OK".equals(body.getCode())) {
                if (verificationRejectedCode(body.getCode())) return false;
                throw providerUnavailable();
            }
            return body.getModel() != null && "PASS".equals(body.getModel().getVerifyResult())
                    && outId.equals(body.getModel().getOutId());
        } catch (TeaException exception) {
            if (verificationRejectedCode(exception.getCode())) return false;
            throw providerUnavailable();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw providerUnavailable();
        }
    }

    static boolean verificationRejectedCode(String code) {
        return code != null && ("isv.ValidateFail".equalsIgnoreCase(code) || "VALIDATE_FAIL".equalsIgnoreCase(code));
    }
    private static ApiException providerUnavailable() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "SMS_PROVIDER_UNAVAILABLE", "短信供应商暂时不可用");
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
