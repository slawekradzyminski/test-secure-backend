package com.awesome.testing.repository;

import com.awesome.testing.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Long> {
    @Query(value = "SELECT DISTINCT ci.username FROM CartItemEntity ci ORDER BY ci.username",
            countQuery = "SELECT COUNT(DISTINCT ci.username) FROM CartItemEntity ci")
    Page<String> findCartOwners(Pageable pageable);

    @Query("SELECT ci FROM CartItemEntity ci JOIN FETCH ci.product WHERE ci.username IN :usernames")
    List<CartItemEntity> findByUsernames(@Param("usernames") List<String> usernames);

    @Query("SELECT ci FROM CartItemEntity ci JOIN FETCH ci.product WHERE ci.username = :username")
    List<CartItemEntity> findByUsername(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ci FROM CartItemEntity ci WHERE ci.username = :username ORDER BY ci.product.id")
    List<CartItemEntity> findByUsernameForUpdate(@Param("username") String username);

    @Query("SELECT ci FROM CartItemEntity ci JOIN FETCH ci.product WHERE ci.username = :username AND ci.product.id = :productId")
    Optional<CartItemEntity> findByUsernameAndProductId(@Param("username") String username, @Param("productId") Long productId);

    void deleteByUsername(String username);

    void deleteByUsernameAndProductId(String username, Long productId);

    long countByUsername(String username);
}
