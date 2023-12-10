package com.awesome.testing;

import java.util.List;

import com.awesome.testing.model.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.awesome.testing.model.Role;
import com.awesome.testing.service.UserService;
import org.springframework.scheduling.annotation.EnableAsync;
import org.zalando.logbook.HeaderFilter;

import static org.zalando.logbook.HeaderFilter.none;

@SpringBootApplication
@EnableAsync
@RequiredArgsConstructor
public class JwtAuthServiceApp implements CommandLineRunner {

    private final UserService userService;

    public static void main(String[] args) {
        SpringApplication.run(JwtAuthServiceApp.class, args);
    }

    @Bean
    public HeaderFilter headerFilter() {
        return none();
    }

    @Override
    public void run(String... params) {
        UserEntity admin = UserEntity.builder()
            .username("admin")
            .password("admin")
            .email("admin@email.com")
            .firstName("Slawomir")
            .lastName("Radzyminski")
            .roles(List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT))
            .build();
        userService.signUp(admin);

        UserEntity client = UserEntity.builder()
            .username("client")
            .password("client")
            .email("client@email.com")
            .firstName("Gosia")
            .lastName("Radzyminska")
            .roles(List.of(Role.ROLE_CLIENT))
            .build();
        userService.signUp(client);
    }

}
