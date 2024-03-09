package com.awesome.testing.service;

import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.dto.specialty.SpecialtyIdDto;
import com.awesome.testing.entities.doctor.SpecialtyEntity;
import com.awesome.testing.repository.SpecialtiesRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
public class SpecialtiesService {

    private final SpecialtiesRepository specialtiesRepository;

    public SpecialtyIdDto addSpecialty(String name) {
        Optional<SpecialtyEntity> specialtyEntity = specialtiesRepository.findByName(name);
        if (specialtyEntity.isPresent()) {
            return new SpecialtyIdDto(specialtyEntity.get().getId());
        }

        specialtiesRepository.save(SpecialtyEntity.builder().name(name).build());
        Integer id = specialtiesRepository.findByName(name).orElseThrow().getId();
        return new SpecialtyIdDto(id);
    }

    public void deleteSpecialty(Integer id) {
        search(id);
        specialtiesRepository.deleteById(id);
    }

    public SpecialtyDto getSpecialty(Integer id) {
        SpecialtyEntity specialtyEntity = search(id);
        return SpecialtyDto.from(specialtyEntity);
    }

    public SpecialtyDto updateSpecialty(Integer id, String name) {
        SpecialtyEntity specialtyEntity = search(id);
        specialtyEntity.setName(name);
        specialtiesRepository.save(specialtyEntity);
        return SpecialtyDto.from(specialtyEntity);
    }

    public List<SpecialtyDto> getAll() {
        return specialtiesRepository.findAll().stream()
                .map(SpecialtyDto::from)
                .toList();
    }

    private SpecialtyEntity search(Integer id) {
        return specialtiesRepository.findById(id)
                .orElseThrow(EntityNotFoundException::new);
    }
}