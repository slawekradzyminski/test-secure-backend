package com.awesome.testing.controller.users;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForDoctorAndAdmin;
import com.awesome.testing.dto.doctor.DoctorTypeUpdateDto;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.service.UserService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserAssignDoctorTypesController {

    private final UserService userService;

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Assign Doctor types to doctor")
    @PutMapping("/doctortypes")
    public UserResponseDto updateDoctorTypes(@RequestBody DoctorTypeUpdateDto doctorTypeUpdateDto) {
        return userService.updateDoctorTypes(doctorTypeUpdateDto.getDoctorTypeIds());
    }
}