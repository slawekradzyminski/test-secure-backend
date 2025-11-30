package com.awesome.testing.service.password;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.entity.UserEntity;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEmailFactory {

    private static final String PROP_USERNAME = "username";
    private static final String PROP_RESET_LINK = "resetLink";
    private static final String PROP_EXPIRES_MINUTES = "expiresInMinutes";

    public EmailDto buildResetRequestEmail(UserEntity user, String resetLink, Duration ttl) {
        String subject = "Password reset requested";
        long minutes = ttl.toMinutes();
        String body = """
                Hi %s,

                We received a request to reset the password for your account on Awesome Shop.
                You can create a new password by visiting the link below. The link remains valid for the next %d minute%s.

                %s

                If you did not request this change, you can safely ignore this email.

                Thanks,
                Awesome Shop Team
                """.formatted(user.getUsername(), minutes, minutes == 1 ? "" : "s", resetLink);

        return EmailDto.builder()
                .to(user.getEmail())
                .subject(subject)
                .message(body)
                .template(EmailTemplate.PASSWORD_RESET_REQUESTED)
                .properties(Map.of(
                        PROP_USERNAME, user.getUsername(),
                        PROP_RESET_LINK, resetLink,
                        PROP_EXPIRES_MINUTES, Long.toString(minutes)
                ))
                .build();
    }

    public EmailDto buildResetConfirmationEmail(UserEntity user) {
        String subject = "Your password has been changed";
        String body = """
                Hi %s,

                This is a confirmation that the password for your Awesome Shop account was just updated.
                If you performed this change, no action is needed. If you did not, please request another reset immediately.

                Thanks,
                Awesome Shop Team
                """.formatted(user.getUsername());

        return EmailDto.builder()
                .to(user.getEmail())
                .subject(subject)
                .message(body)
                .template(EmailTemplate.PASSWORD_RESET_CONFIRMED)
                .properties(Map.of(
                        PROP_USERNAME, user.getUsername()
                ))
                .build();
    }
}
