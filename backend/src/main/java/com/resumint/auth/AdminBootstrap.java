package com.resumint.auth;

import com.resumint.web.ApiException;
import java.time.Clock;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class AdminBootstrap implements ApplicationRunner {
    private static final String RECOVERY_CONFIRMATION = "PROMOTE_ADMIN_AND_AUDIT";
    private final AuthRepository repository;
    private final SmsVerificationService sms;
    private final SecurityAuditRepository audit;
    private final Clock clock;
    private final String configuredPhone;
    private final String mode;
    private final String confirmation;

    AdminBootstrap(AuthRepository repository, SmsVerificationService sms, SecurityAuditRepository audit, Clock clock,
            @Value("${resumint.admin.phone:}") String configuredPhone,
            @Value("${resumint.admin.mode:none}") String mode,
            @Value("${resumint.admin.confirmation:}") String confirmation){
        this.repository=repository;this.sms=sms;this.audit=audit;this.clock=clock;
        this.configuredPhone=configuredPhone;this.mode=mode;this.confirmation=confirmation;
    }

    @Override @Transactional public void run(ApplicationArguments args){
        String operation=mode==null?"none":mode.trim().toLowerCase(Locale.ROOT);
        if("none".equals(operation))return;
        if(!java.util.Set.of("bootstrap","recover").contains(operation))throw new IllegalStateException("resumint.admin.mode must be none, bootstrap, or recover");
        if(configuredPhone==null||configuredPhone.isBlank())throw new IllegalStateException("administrator phone is required for the requested operation");
        if(!RECOVERY_CONFIRMATION.equals(confirmation))throw new IllegalStateException("administrator operation requires the documented one-time confirmation");
        String phone=PhoneNumbers.normalize(configuredPhone);
        AuthRepository.UserRow user=repository.userByPhoneHash(sms.phoneHash(phone))
                .orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"ADMIN_ACCOUNT_NOT_FOUND","管理员账号必须先完成手机号注册"));
        if("bootstrap".equals(operation)&&repository.adminCount()>0)throw new IllegalStateException("bootstrap is only allowed when no administrator exists");
        int changed=repository.promoteAdmin(user.id(),clock.instant());
        audit.record("OFFLINE_OPERATOR",null,"bootstrap".equals(operation)?"ADMIN_BOOTSTRAPPED":"ADMIN_RECOVERED",user.id(),
                "{\"mode\":\""+operation+"\",\"changed\":"+(changed==1)+"}",clock.instant());
    }
}
