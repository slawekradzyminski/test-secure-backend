package com.awesome.testing.repository;

import com.awesome.testing.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    @Query("SELECT ci FROM CartItemEntity ci JOIN FETCH ci.product WHERE ci.username = :username")
    List<CartItemEntity> findByUsername(@Param("username") String username);

    @Query("SELECT ci FROM CartItemEntity ci JOIN FETCH ci.product WHERE ci.username = :username AND ci.product.id = :productId")
    Optional<CartItemEntity> findByUsernameAndProductId(@Param("username") String username, @Param("productId") Long productId);

    void deleteByUsername(String username);

    void deleteByUsernameAndProductId(String username, Long productId);
} 