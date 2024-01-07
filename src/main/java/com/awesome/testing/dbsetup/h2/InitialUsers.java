package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;

import java.util.List;

public class InitialUsers {

    static UserRegisterDto getDoctor() {
        return UserRegisterDto.builder()
                .username("doctor")
                .password("doctor")
                .email("doctor@email.com")
                .firstName("Ferdynant")
                .lastName("Nowak")
                .roles(List.of(Role.ROLE_DOCTOR, Role.ROLE_ADMIN))
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
