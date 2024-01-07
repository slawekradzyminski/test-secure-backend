package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.repository.DoctorTypeRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.DoctorTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.awesome.testing.dbsetup.h2.DoctorTypesSetup.DOCTOR_SPECIALTIES;
import static com.awesome.testing.dbsetup.h2.InitialUsers.getDoctor;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class AssignDoctorTypesSetup {

    private final DoctorTypeService doctorTypeService;
    private final DoctorTypeRepository doctorTypeRepository;
    private final UserRepository userRepository;

    public void assignDoctorTypesForDoctor() {
        UserEntity user = userRepository.findByUsername(getDoctor().getUsername());
        List<Integer> doctorTypeIds = doctorTypeService.getAll().stream()
                .filter(it -> DOCTOR_SPECIALTIES.contains(it.getDoctorType()))
                .map(DoctorTypeDto::getId)
                .toList();
        List<DoctorTypeEntity> doctorTypes = doctorTypeRepository.findAllById(doctorTypeIds);
        user.setDoctorTypes(doctorTypes);
        userRepository.save(user);
    }

}
