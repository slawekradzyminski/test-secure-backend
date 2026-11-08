package com.awesome.testing.repository.wishlist;

import com.awesome.testing.entity.wishlist.WishlistItemEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WishlistItemRepository extends JpaRepository<WishlistItemEntity, Long> {

    @EntityGraph(attributePaths = "product")
    List<WishlistItemEntity> findByUsernameOrderByCreatedAtDescIdDesc(String username);

    Optional<WishlistItemEntity> findByUsernameAndProductId(String username, Long productId);

    long countByUsername(String username);

    List<WishlistItemEntity> findByProductIdAndNotifyWhenAvailableTrue(Long productId);

    @Modifying
    @Query("delete from WishlistItemEntity w where w.username = :username")
    void deleteByUsername(@Param("username") String username);
}
