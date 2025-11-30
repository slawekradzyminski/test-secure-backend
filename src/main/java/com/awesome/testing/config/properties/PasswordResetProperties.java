package com.awesome.testing.config.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "password-reset")
public class PasswordResetProperties {

    /**
     * Default base URL used to build reset links (e.g. http://localhost:8081/reset).
     */
    private String frontendBaseUrl = "http://localhost:8081/reset";

    /**
     * How long tokens remain valid.
     */
    private Duration tokenTtl = Duration.ofMinutes(30);

    /**
     * Number of random bytes used to build the token (Base64 URL encoded).
     */
    private int tokenByteLength = 32;

    /**
     * Whether responses should echo the token back (useful locally, off in prod).
     */
    private boolean exposeTokenInResponse = false;

    /**
     * Whether local profile should store outgoing emails in an in-memory outbox.
     */
    private boolean localOutboxEnabled = true;
}
