package com.awesome.testing;

import com.awesome.testing.fakedata.SetupData;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class JwtAuthServiceApp implements CommandLineRunner {

    @Autowired(required = false)
    private SetupData setupData;

    public static void main(String[] args) {
        SpringApplication.run(JwtAuthServiceApp.class, args);
    }

    @Override
    public void run(String... params) {
        if (setupData != null) {
            setupData.setupData();
        }
    }
}
