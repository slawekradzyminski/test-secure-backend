package com.awesome.testing.security.ratelimit;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.rate-limit")
@Getter
@Setter
public class RateLimitProperties {

    private boolean enabled = false;
    private boolean trustForwardedHeaders = false;
    private Cache cache = new Cache();
    private Policies policies = new Policies();

    @Getter
    @Setter
    public static class Cache {
        private long maximumSize = 10_000;
        private Duration expireAfterAccess = Duration.ofHours(1);
    }

    @Getter
    @Setter
    public static class Policies {
        private Policy signupIp = new Policy(10, Duration.ofHours(1));
        private Policy signinIp = new Policy(20, Duration.ofMinutes(5));
        private Policy signinUsername = new Policy(10, Duration.ofMinutes(15));
        private Policy signinIpUsername = new Policy(5, Duration.ofMinutes(5));
        private Policy passwordForgotIp = new Policy(10, Duration.ofMinutes(15));
        private Policy passwordForgotIdentifier = new Policy(3, Duration.ofMinutes(30));
        private Policy passwordResetIp = new Policy(10, Duration.ofMinutes(15));
        private Policy refreshIp = new Policy(60, Duration.ofMinutes(15));
        private Policy emailUser = new Policy(30, Duration.ofMinutes(15));
        private Policy qrUser = new Policy(60, Duration.ofMinutes(15));
        private Policy ollamaUser = new Policy(20, Duration.ofMinutes(5));
        private Policy ollamaIp = new Policy(20, Duration.ofMinutes(5));
    }

    @Getter
    @Setter
    public static class Policy {
        private int capacity;
        private Duration window;

        public Policy() {
        }

        public Policy(int capacity, Duration window) {
            this.capacity = capacity;
            this.window = window;
        }

        public boolean isActive() {
            return capacity > 0 && window != null && !window.isZero() && !window.isNegative();
        }
    }
}
