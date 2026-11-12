package com.awesome.testing.security;

import com.awesome.testing.dto.validation.MaxUtf8BytesValidator;

/**
 * BCrypt uses only the first 72 bytes of a password. Longer passwords are rejected when they are set
 * (signup, reset) and never match at sign-in, so two passwords that differ after byte 72 are never
 * treated as the same password.
 */
public final class PasswordPolicy {

    public static final int MAX_BYTES = 72;
    public static final String MAX_BYTES_MESSAGE = "Maximum password length: 72 bytes";

    private PasswordPolicy() {
    }

    public static boolean exceedsBcryptLimit(String password) {
        return password != null && MaxUtf8BytesValidator.utf8Length(password) > MAX_BYTES;
    }
}
