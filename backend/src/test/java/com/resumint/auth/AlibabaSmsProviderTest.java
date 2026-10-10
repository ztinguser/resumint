package com.resumint.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlibabaSmsProviderTest {
    @Test void treatsAlibabaValidationFailureAsRejectedCode() {
        assertThat(AlibabaSmsProvider.verificationRejectedCode("isv.ValidateFail")).isTrue();
        assertThat(AlibabaSmsProvider.verificationRejectedCode("ISV.VALIDATEFAIL")).isTrue();
        assertThat(AlibabaSmsProvider.verificationRejectedCode("AccessDenied")).isFalse();
        assertThat(AlibabaSmsProvider.verificationRejectedCode(null)).isFalse();
    }
}
