package com.awesome.testing.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.awesome.testing.entities.doctor.DoctorTypeEntity;

public interface DoctorTypeRepository extends JpaRepository<DoctorTypeEntity, Integer> {

    DoctorTypeEntity findByDoctorType(String doctorType);

}
