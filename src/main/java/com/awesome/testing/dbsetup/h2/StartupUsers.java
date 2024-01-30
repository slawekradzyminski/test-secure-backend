package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Supplier;

import static com.awesome.testing.dbsetup.h2.H2DbSetup.FAKER;

@Slf4j
public class StartupUsers {

    static UserRegisterDto getDoctor(String specialty) {
        String username = specialty.replace(" ", "");
        return UserRegisterDto.builder()
                .username(username)
                .password("password")
                .email(String.format("%s@email.com", username))
                .firstName(draw(() -> FAKER.name().firstName()))
                .lastName(draw(() -> FAKER.name().lastName()))
                .roles(List.of(Role.ROLE_DOCTOR))
                .build();
    }

    static UserRegisterDto getDoctor() {
        return UserRegisterDto.builder()
                .username("doctor")
                .password("doctor")
                .email(FAKER.internet().emailAddress())
                .firstName(draw(() -> FAKER.name().firstName()))
                .lastName(draw(() -> FAKER.name().lastName()))
                .roles(List.of(Role.ROLE_DOCTOR))
                .build();
    }

    static UserRegisterDto getClient() {
        return UserRegisterDto.builder()
                .username("client")
                .password("client")
                .email("client@email.com")
                .firstName("Gosia")
                .lastName("Radzyminska")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
    }

    static UserRegisterDto getAdmin() {
        return UserRegisterDto.builder()
                .username("admin")
                .password("admin")
                .email("admin@email.com")
                .firstName("Slawomir")
                .lastName("Radzyminski")
                .roles(List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT))
                .build();
    }

    private static String draw(Supplier<String> supplier) {
        int attempt = 1;
        String result = "";
        while (attempt <= 20) {
            result = supplier.get();
            if (result.length() >= 3) break;
            attempt++;
            log.info("Failed to draw a String which has at least 3 characters. Performing attempt {}", attempt);
        }
        if (attempt > 1) {
            log.info("Success on attempt {}", attempt);
        }
        return result;
    }

}
