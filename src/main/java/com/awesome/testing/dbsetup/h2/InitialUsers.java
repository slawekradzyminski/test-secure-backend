package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;

import java.util.List;

import static com.awesome.testing.dbsetup.h2.H2DbSetup.FAKER;

public class InitialUsers {

    static UserRegisterDto getDoctor(String specialty) {
        String username = specialty.replace(" ", "");
        return UserRegisterDto.builder()
                .username(username)
                .password("password")
                .email(String.format("%s@email.com", username))
                .firstName(FAKER.name().firstName())
                .lastName(FAKER.name().lastName())
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

}
