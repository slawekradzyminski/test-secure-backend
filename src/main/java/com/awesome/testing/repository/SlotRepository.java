package com.awesome.testing.repository;

import com.awesome.testing.entities.slot.SlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SlotRepository extends JpaRepository<SlotEntity, Integer> {

}
