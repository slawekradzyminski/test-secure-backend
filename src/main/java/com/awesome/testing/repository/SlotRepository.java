package com.awesome.testing.repository;

import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.user.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface SlotRepository extends JpaRepository<SlotEntity, Integer> {

    boolean existsByDoctorAndStartTimeBetween(UserEntity doctor, LocalDateTime start, LocalDateTime end);
}
