package com.resumint.auth;

import com.resumint.web.ApiException;
import org.springframework.http.HttpStatus;

final class PhoneNumbers {
    private PhoneNumbers() { }

    static String normalize(String input) {
        String value = input == null ? "" : input.replaceAll("[\\s-]", "");
        if (value.startsWith("+86")) value = value.substring(3);
        else if (value.startsWith("86") && value.length() == 13) value = value.substring(2);
        if (!value.matches("1[3-9]\\d{9}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PHONE", "仅支持有效的中国大陆 +86 手机号");
        }
        return "+86" + value;
    }

    static String national(String e164) { return e164.substring(3); }

    static String mask(String e164) {
        String value = national(e164);
        return "+86 " + value.substring(0, 3) + "****" + value.substring(7);
    }
}
