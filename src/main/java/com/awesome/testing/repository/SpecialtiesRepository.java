package com.awesome.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.awesome.testing.entities.doctor.SpecialtyEntity;

import java.util.Optional;

public interface SpecialtiesRepository extends JpaRepository<SpecialtyEntity, Integer> {

    Optional<SpecialtyEntity> findByName(String name);

}
