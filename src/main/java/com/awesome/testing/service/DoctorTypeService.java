package com.awesome.testing.service;

import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeIdDto;
import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import com.awesome.testing.repository.DoctorTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class DoctorTypeService {

    private final DoctorTypeRepository doctorTypeRepository;

    public DoctorTypeIdDto addDoctorType(String doctorType) {
        Optional<DoctorTypeEntity> doctorTypeEntity = doctorTypeRepository.findByDoctorType(doctorType);
        if (doctorTypeEntity.isPresent()) {
            return new DoctorTypeIdDto(doctorTypeEntity.get().getId());
        }

        doctorTypeRepository.save(DoctorTypeEntity.builder().doctorType(doctorType).build());
        Integer id = doctorTypeRepository.findByDoctorType(doctorType).orElseThrow().getId();
        return new DoctorTypeIdDto(id);
    }

    public void deleteDoctorType(Integer id) {
        search(id);
        doctorTypeRepository.deleteById(id);
    }

    public DoctorTypeDto getDoctorType(Integer id) {
        DoctorTypeEntity doctorTypeEntity = search(id);
        return DoctorTypeDto.from(doctorTypeEntity);
    }

    public DoctorTypeDto updateDoctorType(Integer id, String doctorType) {
        DoctorTypeEntity doctorTypeEntity = search(id);
        doctorTypeEntity.setDoctorType(doctorType);
        doctorTypeRepository.save(doctorTypeEntity);
        return DoctorTypeDto.from(doctorTypeEntity);
    }

    public List<DoctorTypeDto> getAll() {
        return doctorTypeRepository.findAll().stream()
                .map(DoctorTypeDto::from)
                .toList();
    }

    private DoctorTypeEntity search(Integer id) {
        return doctorTypeRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
    }
}