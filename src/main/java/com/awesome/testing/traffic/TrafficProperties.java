package com.awesome.testing.traffic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Validated
@ConfigurationProperties(prefix = "app.traffic")
@Getter
@Setter
public class TrafficProperties {

    private static final int DEFAULT_MAX_PAGE_SIZE = 100;

    @Min(1)
    @Max(100000)
    private int eventQueueCapacity = 1000;

    @Min(1)
    @Max(10000)
    private int persistenceQueueCapacity = 1000;

    @Min(1)
    @Max(1000)
    private int persistenceBatchSize = 100;

    private int maxBodyLength = 4000;
    private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    private Duration retention = Duration.ofDays(1);
    private Duration cleanupInterval = Duration.ofDays(1);
    /**
     * Masks email addresses in captured traffic. Secrets (passwords, tokens, 2FA and gift card codes,
     * credential headers) are masked in every profile and cannot be switched off.
     */
    private boolean obfuscateEmails;
    /**
     * Lets anonymous callers reach the traffic endpoints without a session header. They receive an empty
     * page and no STOMP events: traffic records are readable only by the user who made them.
     */
    private boolean legacyPublicAccess;
    private List<String> excludedPaths =
            new ArrayList<>(
                    List.of(
                            "/api/v1/traffic/logs",
                            "/api/v1/traffic/logs/",
                            "/api/v1/users/2fa",
                            "/api/v1/users/signin/2fa",
                            "/v3/api-docs",
                            "/v3/api-docs.yaml",
                            "/v3/api-docs/"));
}
