package com.resumint.auth;

import com.aliyun.dypnsapi20170525.Client;
import com.aliyun.dypnsapi20170525.models.CheckSmsVerifyCodeRequest;
import com.aliyun.dypnsapi20170525.models.SendSmsVerifyCodeRequest;
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
    private final String templateCode;
    private final String schemeName;
    private final long validSeconds;

    AlibabaSmsProvider(
            @Value("${resumint.sms.endpoint}") String endpoint,
            @Value("${resumint.sms.sign-name}") String signName,
            @Value("${resumint.sms.template-code}") String templateCode,
            @Value("${resumint.sms.scheme-name}") String schemeName,
            @Value("${resumint.auth.phone-lookup-secret}") String phoneLookupSecret,
            @Value("${resumint.sms.code-minutes}") long codeMinutes) throws Exception {
        String accessKeyId = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        if (blank(accessKeyId) || blank(accessKeySecret) || blank(signName) || blank(templateCode) || blank(schemeName)) {
            throw new IllegalStateException("Alibaba SMS requires environment credentials, sign name, template code and scheme name");
        }
        if (phoneLookupSecret.startsWith("dev-only-")) {
            throw new IllegalStateException("Alibaba SMS requires a non-default phone lookup secret");
        }
        Config config = new Config().setAccessKeyId(accessKeyId).setAccessKeySecret(accessKeySecret).setEndpoint(endpoint);
        this.client = new Client(config);
        this.signName = signName;
        this.templateCode = templateCode;
        this.schemeName = schemeName;
        this.validSeconds = codeMinutes * 60;
    }

    @Override public String name() { return "alibaba"; }

    @Override public SendResult send(String phoneNational, String outId) {
        try {
            SendSmsVerifyCodeRequest request = new SendSmsVerifyCodeRequest()
                    .setPhoneNumber(phoneNational).setCountryCode("86").setSignName(signName)
                    .setTemplateCode(templateCode).setTemplateParam("{\"code\":\"##code##\",\"min\":\"" + validSeconds / 60 + "\"}")
                    .setOutId(outId).setSchemeName(schemeName)
                    .setCodeLength(6L).setCodeType(1L).setDuplicatePolicy(1L)
                    .setValidTime(validSeconds).setReturnVerifyCode(false);
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
                    .setPhoneNumber(phoneNational).setCountryCode("86").setOutId(outId)
                    .setSchemeName(schemeName).setVerifyCode(code);
            var body = client.checkSmsVerifyCode(request).getBody();
            return body != null && Boolean.TRUE.equals(body.getSuccess()) && "OK".equals(body.getCode())
                    && body.getModel() != null && "PASS".equals(body.getModel().getVerifyResult())
                    && outId.equals(body.getModel().getOutId());
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "SMS_PROVIDER_UNAVAILABLE", "短信供应商暂时不可用");
        }
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
