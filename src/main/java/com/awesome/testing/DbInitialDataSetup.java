package com.awesome.testing;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.service.DoctorTypeService;
import com.awesome.testing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DbInitialDataSetup {

    private static final List<String> SPECIALTIES = List.of(
            "Pediatrician",
            "Psychiatrist",
            "Cardiologist",
            "Endocrinologist",
            "Neurologist",
            "Gastroenterologist",
            "Dermatologist",
            "Radiologist",
            "Pathologist",
            "Surgeon",
            "Anesthesiologist",
            "Ophthalmologist",
            "Orthopedist",
            "Urologist",
            "Obstetrician gynecologist",
            "Oncologist",
            "Pulmonologist",
            "Nephrologist",
            "Allergist immunologist",
            "Infectious disease specialist",
            "Rheumatologist",
            "Geriatrician",
            "Sports medicine specialist",
            "Physical medicine rehabilitation",
            "Pain management specialist",
            "Palliative care specialist",
            "Sleep medicine specialist",
            "Plastic surgeon",
            "Otolaryngologist"
    );

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

        SPECIALTIES.forEach(doctorTypeService::addDoctorType);
    }



}
