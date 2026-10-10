package com.resumint.auth;

interface SmsProvider {
    String name();
    SendResult send(String phoneNational, String outId);
    boolean verify(String phoneNational, String outId, String code);

    record SendResult(String providerRequestId) { }
}
