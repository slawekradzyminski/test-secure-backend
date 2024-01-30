package com.awesome.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.awesome.testing.entities.doctor.DoctorTypeEntity;

import java.util.Optional;

public interface DoctorTypeRepository extends JpaRepository<DoctorTypeEntity, Integer> {

    Optional<DoctorTypeEntity> findByDoctorType(String doctorType);

}
