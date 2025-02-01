package com.awesome.testing;

import com.awesome.testing.fakedata.SetupData;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import static org.modelmapper.config.Configuration.AccessLevel.PRIVATE;
import static org.modelmapper.convention.MatchingStrategies.STRICT;

@SpringBootApplication
public class JwtAuthServiceApp implements CommandLineRunner {

    @Autowired
    private SetupData setupData;

    public static void main(String[] args) {
        SpringApplication.run(JwtAuthServiceApp.class, args);
    }

    @Bean
    public ModelMapper modelMapper() {
        ModelMapper modelMapper = new ModelMapper();
        modelMapper.getConfiguration()
                .setMatchingStrategy(STRICT)
                .setFieldMatchingEnabled(true)
                .setFieldAccessLevel(PRIVATE)
                .setSkipNullEnabled(true)
                .setPropertyCondition(context -> context.getSource() != null);
        return modelMapper;
    }

    @Override
    public void run(String... params) {
        setupData.setupData();
    }
}
