package com.awesome.testing.traffic;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.traffic")
@Getter
@Setter
public class TrafficProperties {

    private static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private int maxBodyLength = 4000;
    private int maxPageSize = DEFAULT_MAX_PAGE_SIZE;
    private Duration retention = Duration.ofDays(1);
    private Duration cleanupInterval = Duration.ofDays(1);
    private boolean obfuscateAuthorization;
    private boolean obfuscateEmails;
    private boolean obfuscateSensitiveBodyFields;
    private List<String> excludedPaths = new ArrayList<>(List.of(
            "/api/v1/traffic/logs",
            "/api/v1/traffic/logs/",
            "/v3/api-docs",
            "/v3/api-docs/"
    ));
}
