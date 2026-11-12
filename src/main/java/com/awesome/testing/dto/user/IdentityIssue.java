package com.awesome.testing.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Why an existing account does not meet the current username and email rules")
public enum IdentityIssue {
    /** The username is not 4 to 255 letters, digits, dots, underscores or hyphens. */
    USERNAME_FORMAT,
    /** Another account has the same username in a different letter case. */
    USERNAME_CASE_DUPLICATE,
    /** The username equals another account's email. */
    USERNAME_IS_ANOTHER_USERS_EMAIL,
    /** The email is blank, has no '@' or contains whitespace. */
    EMAIL_INVALID,
    /** The email is stored with upper-case letters or surrounding whitespace. */
    EMAIL_NOT_CANONICAL,
    /** Another account has the same email in a different letter case. */
    EMAIL_CASE_DUPLICATE
}
