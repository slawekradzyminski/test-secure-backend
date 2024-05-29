package com.awesome.testing;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.entities.doctor.DoctorType;
import com.awesome.testing.service.DoctorTypeService;
import com.awesome.testing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DbInitialDataSetup {

    private final UserService userService;
    private final DoctorTypeService doctorTypeService;

    public void setupData() {
        UserRegisterDTO admin = UserRegisterDTO.builder()
                .username("admin")
                .password("admin")
                .email("admin@email.com")
                .firstName("Slawomir")
                .lastName("Radzyminski")
                .roles(List.of(Role.ROLE_ADMIN, Role.ROLE_CLIENT))
                .build();
        userService.signUp(admin);

        UserRegisterDTO client = UserRegisterDTO.builder()
                .username("client")
                .password("client")
                .email("client@email.com")
                .firstName("Gosia")
                .lastName("Radzyminska")
                .roles(List.of(Role.ROLE_CLIENT))
                .build();
        userService.signUp(client);

        Arrays.stream(DoctorType.values()).forEach(doctorTypeService::addDoctorType);
    }

}
