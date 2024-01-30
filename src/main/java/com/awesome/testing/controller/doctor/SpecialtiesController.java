package com.awesome.testing.controller.doctor;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForDoctorAndAdmin;
import com.awesome.testing.dto.specialty.CreateSpecialtyDto;
import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.dto.specialty.SpecialtyIdDto;
import com.awesome.testing.service.SpecialtiesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = { "http://localhost:8081", "http://127.0.0.1:8081" }, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/specialties")
@RequiredArgsConstructor
public class SpecialtiesController {

    private final SpecialtiesService specialtiesService;

    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Get specialty")
    @GetMapping("/{id}")
    public SpecialtyDto getSpecialty(@PathVariable Integer id) {
        return specialtiesService.getSpecialty(id);
    }

    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Get all specialties")
    @GetMapping
    public List<SpecialtyDto> getSpecialties() {
        return specialtiesService.getAll();
    }

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Add specialty")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpecialtyIdDto addSpecialty(@RequestBody @Valid CreateSpecialtyDto createSpecialtyDto) {
        return specialtiesService.addSpecialty(createSpecialtyDto.getName());
    }

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Delete specialty")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    public void deleteSpecialty(@PathVariable Integer id) {
        specialtiesService.deleteSpecialty(id);
    }

    @PreAuthorizeForDoctorAndAdmin
    @OperationWithSecurity(summary = "Edit specialty")
    @PutMapping("/{id}")
    public SpecialtyDto editSpecialty(@PathVariable Integer id,
                                       @RequestBody CreateSpecialtyDto createSpecialtyDto) {
        return specialtiesService.updateSpecialty(id, createSpecialtyDto.getName());
    }
}