package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.repository.SpecialtiesRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.service.SpecialtiesService;
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

    private final SpecialtiesService specialtiesService;
    private final SpecialtiesRepository specialtiesRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public void assignSpecialtiesForDoctor() {
        Map<UserRegisterDto, String> doctorSpecialtyMap = SPECIALTIES.stream()
                .collect(Collectors.toMap(
                        StartupUsers::getDoctor,
                        Function.identity()
                ));
        doctorSpecialtyMap.keySet().forEach(userService::signUp);
        doctorSpecialtyMap.forEach(this::setupSpecialties);
    }

    private void setupSpecialties(UserRegisterDto doctor, String specialty) {
        UserEntity user = userRepository.findByUsername(doctor.getUsername());
        List<Integer> specialtiesIds = specialtiesService.getAll()
                .stream()
                .filter(it -> it.getName().equals(specialty))
                .map(SpecialtyDto::getId)
                .toList();
        user.setSpecialties(specialtiesRepository.findAllById(specialtiesIds));
        userRepository.save(user);
    }

}
