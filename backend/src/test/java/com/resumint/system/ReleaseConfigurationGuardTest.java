package com.resumint.system;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseConfigurationGuardTest {
    private static final String ACTIVE="active-secret-containing-at-least-32-characters";
    private static final String PREVIOUS="previous-secret-containing-at-least-32-characters";

    @Test void acceptsSecureReleaseSettingsAndKeyRings(){
        assertThatNoException().isThrownBy(()->guard(ACTIVE+","+PREVIOUS,ACTIVE+","+PREVIOUS,ACTIVE,true,"alibaba",true,"smtp.163.com","alerts@163.com","authorization-code","alerts@163.com","owner@example.com",Path.of("C:/resumint/private")));
    }

    @Test void rejectsDefaultsUnsafeCookiesAndRelativeStorage(){
        assertThatThrownBy(()->guard("dev-only-phone-lookup-secret-change-me",ACTIVE,ACTIVE,true,"alibaba",true,"smtp.163.com","alerts@163.com","code","alerts@163.com","owner@example.com",Path.of("C:/resumint/private"))).hasMessageContaining("AUTH_PHONE_LOOKUP_SECRETS");
        assertThatThrownBy(()->guard(ACTIVE,ACTIVE,ACTIVE,false,"alibaba",true,"smtp.163.com","alerts@163.com","code","alerts@163.com","owner@example.com",Path.of("C:/resumint/private"))).hasMessageContaining("secure cookies");
        assertThatThrownBy(()->guard(ACTIVE,ACTIVE,ACTIVE,true,"disabled",true,"smtp.163.com","alerts@163.com","code","alerts@163.com","owner@example.com",Path.of("C:/resumint/private"))).hasMessageContaining("Alibaba");
        assertThatThrownBy(()->guard(ACTIVE,ACTIVE,ACTIVE,true,"alibaba",false,"smtp.163.com","alerts@163.com","code","alerts@163.com","owner@example.com",Path.of("C:/resumint/private"))).hasMessageContaining("email alerts");
        assertThatThrownBy(()->guard(ACTIVE,ACTIVE,ACTIVE,true,"alibaba",true,"smtp.163.com","alerts@163.com","","alerts@163.com","owner@example.com",Path.of("C:/resumint/private"))).hasMessageContaining("AUTHORIZATION_CODE");
        assertThatThrownBy(()->guard(ACTIVE,ACTIVE,ACTIVE,true,"alibaba",true,"smtp.163.com","alerts@163.com","code","alerts@163.com","owner@example.com",Path.of("relative"))).hasMessageContaining("absolute");
    }

    private static ReleaseConfigurationGuard guard(String phone,String export,String ops,boolean secure,String sms,
            boolean emailEnabled,String host,String username,String password,String from,String to,Path storage){
        return new ReleaseConfigurationGuard(phone,export,ops,secure,sms,emailEnabled,host,username,password,from,to,storage);
    }
}
