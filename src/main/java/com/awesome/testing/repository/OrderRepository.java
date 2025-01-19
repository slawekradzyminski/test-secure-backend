package com.awesome.testing.repository;

import com.awesome.testing.model.Order;
import com.awesome.testing.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.username = :username")
    Page<Order> findByUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.username = :username AND o.status = :status")
    Page<Order> findByUsernameAndStatus(@Param("username") String username, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id AND o.username = :username")
    Optional<Order> findByIdAndUsername(@Param("id") Long id, @Param("username") String username);
} 