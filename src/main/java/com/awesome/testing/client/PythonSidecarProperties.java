package com.awesome.testing.client;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "python.sidecar")
public class PythonSidecarProperties {

    private String url;
    private int timeout;
    private int maxConnections;
    private String maxBufferSize;

}

