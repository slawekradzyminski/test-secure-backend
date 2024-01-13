package com.awesome.testing.controller.doctor;

import com.awesome.testing.dto.doctor.CreateDoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeIdDto;
import com.awesome.testing.service.DoctorTypeService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/doctortypes")
@RequiredArgsConstructor
public class DoctorTypeController {

    private final DoctorTypeService doctorTypeService;

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR')")
    @Operation(summary = "Add doctor type",
            security = {@SecurityRequirement(name = "Authorization")})
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorTypeIdDto addDoctorType(@RequestBody @Valid CreateDoctorTypeDto createDoctorTypeDto) {
        return doctorTypeService.addDoctorType(createDoctorTypeDto.getDoctorType());
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR')")
    @Operation(summary = "Delete doctor type",
            security = {@SecurityRequirement(name = "Authorization")})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void deleteDoctorType(@PathVariable Integer id) {
        doctorTypeService.deleteDoctorType(id);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR') or hasRole('ROLE_CLIENT')")
    @Operation(summary = "Get doctor type",
            security = {@SecurityRequirement(name = "Authorization")})
    @GetMapping("/{id}")
    public DoctorTypeDto getDoctorType(@PathVariable Integer id) {
        return doctorTypeService.getDoctorType(id);
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR') or hasRole('ROLE_CLIENT')")
    @Operation(summary = "Get all doctor types",
            security = {@SecurityRequirement(name = "Authorization")})
    @GetMapping
    public List<DoctorTypeDto> getDoctorType() {
        return doctorTypeService.getAll();
    }

    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_DOCTOR')")
    @Operation(summary = "Edit doctor type",
            security = {@SecurityRequirement(name = "Authorization")})
    @PutMapping("/{id}")
    public DoctorTypeDto editDoctorType(@PathVariable Integer id,
                                        @RequestBody CreateDoctorTypeDto createDoctorTypeDto) {
        return doctorTypeService.updateDoctorType(id, createDoctorTypeDto.getDoctorType());
    }
}