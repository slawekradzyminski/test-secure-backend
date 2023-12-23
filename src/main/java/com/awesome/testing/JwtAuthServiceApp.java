package com.awesome.testing;

import java.awt.image.BufferedImage;

import com.awesome.testing.dbsetup.DbInitialDataSetup;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import org.springframework.http.converter.BufferedImageHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableAsync;
import org.zalando.logbook.HeaderFilter;

import static org.zalando.logbook.HeaderFilter.none;

@SpringBootApplication
@EnableAsync
@RequiredArgsConstructor
public class JwtAuthServiceApp implements CommandLineRunner {

    private final DbInitialDataSetup dbInitialDataSetup;

    public static void main(String[] args) {
        SpringApplication.run(JwtAuthServiceApp.class, args);
    }

    @Bean
    public HeaderFilter headerFilter() {
        return none();
    }

    @Bean
    public HttpMessageConverter<BufferedImage> createImageHttpMessageConverter() {
        return new BufferedImageHttpMessageConverter();
    }

    @Override
    public void run(String... params) {
        dbInitialDataSetup.setupData();
    }

}
