package com.awesome.testing.config.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mfa")
@Getter
@Setter
public class MfaProperties {

    private String issuer = "Awesome Testing";
    private String encryptionPassword;
    private String encryptionSalt;
    private boolean requireSecureEncryption;
    private Duration challengeTtl = Duration.ofMinutes(5);
    private Duration setupTtl = Duration.ofMinutes(15);
    private Duration period = Duration.ofSeconds(30);
    private int adjacentTimeSteps = 1;
    private int secretBits = 160;
    private int recoveryCodeCount = 8;
    private int challengeTokenBytes = 32;
}
