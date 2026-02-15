package com.awesome.testing;

import com.awesome.testing.fakedata.SetupData;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@SpringBootApplication
@Slf4j
public class JwtAuthServiceApp implements CommandLineRunner {

    @Autowired(required = false)
    private SetupData setupData;

    public static void main(String[] args) {
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
}
