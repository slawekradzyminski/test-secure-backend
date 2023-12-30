package com.awesome.testing.repository;

import com.awesome.testing.entities.slot.AppointmentSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AppointmentSlotRepository extends JpaRepository<AppointmentSlotEntity, Integer> {

}
