package com.awesome.testing.repository;

import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.dto.order.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.username = :username")
    Page<OrderEntity> findByUsername(@Param("username") String username, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.username = :username AND o.status = :status")
    Page<OrderEntity> findByUsernameAndStatus(@Param("username") String username, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.id = :id AND o.username = :username")
    Optional<OrderEntity> findByIdAndUsername(@Param("id") Long id, @Param("username") String username);
    
    @Query(value = "SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items",
           countQuery = "SELECT COUNT(o) FROM OrderEntity o")
    Page<OrderEntity> findAllOrdersWithItems(Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM OrderEntity o LEFT JOIN FETCH o.items WHERE o.status = :status",
           countQuery = "SELECT COUNT(o) FROM OrderEntity o WHERE o.status = :status")
    Page<OrderEntity> findAllOrdersByStatus(@Param("status") OrderStatus status, Pageable pageable);
} 