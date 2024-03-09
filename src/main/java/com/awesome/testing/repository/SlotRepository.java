package com.awesome.testing.repository;

import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.slot.SlotStatus;
import com.awesome.testing.entities.user.UserEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SlotRepository extends JpaRepository<SlotEntity, Integer> {

    boolean existsByDoctorAndStartTimeBetween(UserEntity doctor, LocalDateTime start, LocalDateTime end);

    List<SlotEntity> findByClientAndStatus(UserEntity client, SlotStatus status);

    @Query("""
            SELECT s FROM SlotEntity s
            JOIN s.doctor d
            LEFT JOIN d.specialties dt
            WHERE s.startTime >= :startTime AND s.endTime <= :endTime
            AND (:doctorUsername IS NULL OR d.username = :doctorUsername)
            AND (:slotStatus IS NULL OR s.status = :slotStatus)
            AND (:specialtyId IS NULL OR dt.id = :specialtyId)
            """)
    List<SlotEntity> findByCriteria(
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("doctorUsername") String doctorUsername,
            @Param("slotStatus") SlotStatus slotStatus,
            @Param("specialtyId") Integer specialtyId);
}
