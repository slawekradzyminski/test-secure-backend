package com.awesome.testing;

import com.awesome.testing.config.NetworkAddressCachePolicy;
import com.awesome.testing.fakedata.SetupData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.Order;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@Slf4j
@Order(2)
public class JwtAuthServiceApp implements CommandLineRunner {

    @Autowired(required = false)
    private SetupData setupData;

    public static void main(String[] args) {
        usePooledNettyAllocator();
        NetworkAddressCachePolicy.disableNegativeCaching();
        cleanupLogs();
        SpringApplication.run(JwtAuthServiceApp.class, args);
    }

    private static void cleanupLogs() {
        try {
            Path logDir = Paths.get("logs");
            if (Files.exists(logDir) && Files.isDirectory(logDir)) {
                Files.list(logDir).forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException ex) {
                        log.warn("Failed to delete log file: {}", ex.getMessage());
                    }
                });
            } else {
                Files.createDirectories(logDir);
            }
        } catch (IOException ex) {
            log.error("Error cleaning logs directory: {}", ex.getMessage());
        }
    }

    @Override
    public void run(String... params) {
        if (setupData != null) {
            setupData.setupData();
        }
    }

    /**
     * Netty 4.2 (Spring Boot 4.1) defaults to its adaptive buffer allocator. Under it the Artemis client
     * (built for Netty 4.1) lost listener sessions in the native image right after start-up ("Session is
     * closed", IllegalReferenceCountException in ActiveMQFrameDecoder2), so a broker delivery could be
     * rolled back until it was dead-lettered. The pooled allocator, Netty 4.1's default, does not show it.
     * Read at run time, so it must be set before any Netty class loads; an explicit
     * -Dio.netty.allocator.type wins.
     */
    static void usePooledNettyAllocator() {
        String allocator = "io.netty.allocator.type";
        if (System.getProperty(allocator) == null) {
            System.setProperty(allocator, "pooled");
        }
    }
}
