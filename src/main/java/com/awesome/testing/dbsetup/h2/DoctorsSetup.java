package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.repository.DoctorTypeRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.DoctorTypeService;
import com.awesome.testing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.awesome.testing.dbsetup.h2.SpecialtiesSetup.SPECIALTIES;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DoctorsSetup {

    private final DoctorTypeService doctorTypeService;
    private final DoctorTypeRepository doctorTypeRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public void assignDoctorTypesForDoctor() {
        Map<UserRegisterDto, String> doctorSpecialtyMap = SPECIALTIES.stream()
                .collect(Collectors.toMap(
                        InitialUsers::getDoctor,
                        Function.identity()
                ));
        doctorSpecialtyMap.keySet().forEach(userService::signUp);
        doctorSpecialtyMap.forEach(this::setupSpecialties);
    }

    private void setupSpecialties(UserRegisterDto doctor, String specialty) {
        List<DoctorTypeDto> allDoctorTypes = doctorTypeService.getAll();
        UserEntity user = userRepository.findByUsername(doctor.getUsername());
        List<Integer> doctorTypeIds = allDoctorTypes.stream()
                .filter(it -> it.getDoctorType().equals(specialty))
                .map(DoctorTypeDto::getId)
                .toList();
        List<DoctorTypeEntity> doctorTypes = doctorTypeRepository.findAllById(doctorTypeIds);
        user.setDoctorTypes(doctorTypes);
        userRepository.save(user);
    }

}
