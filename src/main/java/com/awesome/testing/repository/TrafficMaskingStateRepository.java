package com.awesome.testing.repository;

import com.awesome.testing.entity.TrafficMaskingStateEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrafficMaskingStateRepository extends JpaRepository<TrafficMaskingStateEntity, Short> {
}
