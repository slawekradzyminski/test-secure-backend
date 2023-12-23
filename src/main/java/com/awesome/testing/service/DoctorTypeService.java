package com.awesome.testing.service;

import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import com.awesome.testing.repository.DoctorTypeRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class DoctorTypeService {

    private final DoctorTypeRepository doctorTypeRepository;

    public void addDoctorType(String doctorType) {
        doctorTypeRepository.save(DoctorTypeEntity.builder().doctorType(doctorType).build());
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