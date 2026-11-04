package com.adrotation.crm.shared.exception;

public class EmailVerificationRequiredException extends RuntimeException {
    private final String email;

    public EmailVerificationRequiredException(String email) {
        super("Подтвердите email кодом из письма");
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
