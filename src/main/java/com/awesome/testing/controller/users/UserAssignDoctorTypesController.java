package com.awesome.testing.controller.users;

import com.awesome.testing.dto.doctor.DoctorTypeUpdateDto;
import com.awesome.testing.dto.users.UserResponseDTO;
import com.awesome.testing.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAssignDoctorTypesController {

    private final UserService userService;

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR')")
    @Operation(summary = "Assign Doctor types to doctor",
            security = {@SecurityRequirement(name = "Authorization")})
    @PutMapping("/doctortypes")
    public UserResponseDTO updateDoctorTypes(@RequestBody DoctorTypeUpdateDto doctorTypeUpdateDTO) {
        return userService.updateDoctorTypes(doctorTypeUpdateDTO.getDoctorTypeIds());
    }
}