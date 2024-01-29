package com.awesome.testing.controller.doctor;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForDoctorAndAdmin;
import com.awesome.testing.dto.doctor.CreateDoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeIdDto;
import com.awesome.testing.service.DoctorTypeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" }, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/doctortypes")
@RequiredArgsConstructor
public class DoctorTypeController {

    private final DoctorTypeService doctorTypeService;

    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Get doctor type")
    @GetMapping("/{id}")
    public DoctorTypeDto getDoctorType(@PathVariable Integer id) {
        return doctorTypeService.getDoctorType(id);
    }

    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Get all doctor types")
    @GetMapping
    public List<DoctorTypeDto> getDoctorType() {
        return doctorTypeService.getAll();
    }

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Add doctor type")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DoctorTypeIdDto addDoctorType(@RequestBody @Valid CreateDoctorTypeDto createDoctorTypeDto) {
        return doctorTypeService.addDoctorType(createDoctorTypeDto.getDoctorType());
    }

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Delete doctor type")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void deleteDoctorType(@PathVariable Integer id) {
        doctorTypeService.deleteDoctorType(id);
    }

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Edit doctor type")
    @PutMapping("/{id}")
    public DoctorTypeDto editDoctorType(@PathVariable Integer id,
            @RequestBody CreateDoctorTypeDto createDoctorTypeDto) {
        return doctorTypeService.updateDoctorType(id, createDoctorTypeDto.getDoctorType());
    }
}