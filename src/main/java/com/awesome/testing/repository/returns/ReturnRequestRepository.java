package com.awesome.testing.repository.returns;

import com.awesome.testing.entity.returns.ReturnRequestEntity;
import com.awesome.testing.entity.returns.ReturnStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequestEntity, Long> {

    List<ReturnRequestEntity> findByOrderIdAndStatusNot(Long orderId, ReturnStatus status);

    Page<ReturnRequestEntity> findByUsername(String username, Pageable pageable);

    Optional<ReturnRequestEntity> findByIdAndUsername(Long id, String username);

    Page<ReturnRequestEntity> findByStatus(ReturnStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ReturnRequestEntity r where r.id = :id")
    Optional<ReturnRequestEntity> findByIdForUpdate(@Param("id") Long id);
}
