package com.awesome.testing.dbsetup;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UsersSetup {

    private final UserService userService;

    public void setupUsers() {
        userService.signUp(getAdmin());
        userService.signUp(getClient());
        userService.signUp(getDoctor());
    }

    private UserRegisterDTO getDoctor() {
        return UserRegisterDTO.builder()
                .username("doctor")
                .password("doctor")
                .email("doctor@email.com")
                .firstName("Ferdynant")
                .lastName("Nowak")
                .roles(List.of(Role.ROLE_DOCTOR, Role.ROLE_ADMIN))
                .build();
    }

    private UserRegisterDTO getClient() {
        return UserRegisterDTO.builder()
                .username("client")
                .password("client")
                .email("client@email.com")
                .firstName("Gosia")
                .lastName("Radzyminska")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
    }

    private UserRegisterDTO getAdmin() {
        return UserRegisterDTO.builder()
                .username("admin")
                .password("admin")
                .email("admin@email.com")
                .firstName("Slawomir")
                .lastName("Radzyminski")
                .roles(List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT))
                .build();
    }

}
