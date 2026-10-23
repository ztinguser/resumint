package com.resumint.system;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ReleaseConfigurationGuardTest {
    private static final String ACTIVE="active-secret-containing-at-least-32-characters";
    private static final String PREVIOUS="previous-secret-containing-at-least-32-characters";

    @Test void acceptsSecureReleaseSettingsAndKeyRings(){
        assertThatNoException().isThrownBy(()->new ReleaseConfigurationGuard(ACTIVE+","+PREVIOUS,ACTIVE+","+PREVIOUS,ACTIVE,true,"alibaba",Path.of("C:/resumint/private")));
    }

    @Test void rejectsDefaultsUnsafeCookiesAndRelativeStorage(){
        assertThatThrownBy(()->new ReleaseConfigurationGuard("dev-only-phone-lookup-secret-change-me",ACTIVE,ACTIVE,true,"alibaba",Path.of("C:/resumint/private"))).hasMessageContaining("AUTH_PHONE_LOOKUP_SECRETS");
        assertThatThrownBy(()->new ReleaseConfigurationGuard(ACTIVE,ACTIVE,ACTIVE,false,"alibaba",Path.of("C:/resumint/private"))).hasMessageContaining("secure cookies");
        assertThatThrownBy(()->new ReleaseConfigurationGuard(ACTIVE,ACTIVE,ACTIVE,true,"disabled",Path.of("C:/resumint/private"))).hasMessageContaining("Alibaba");
        assertThatThrownBy(()->new ReleaseConfigurationGuard(ACTIVE,ACTIVE,ACTIVE,true,"alibaba",Path.of("relative"))).hasMessageContaining("absolute");
    }
}
