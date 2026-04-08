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

    private int maxBodyLength = 4000;
    private Duration retention = Duration.ofDays(1);
    private Duration cleanupInterval = Duration.ofDays(1);
    private boolean obfuscateAuthorization;
    private boolean obfuscateEmails;
    private List<String> excludedPaths = new ArrayList<>(List.of(
            "/api/v1/traffic/logs",
            "/api/v1/traffic/logs/"
    ));
}
