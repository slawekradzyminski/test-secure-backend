package com.awesome.testing.repository.coupon;

import com.awesome.testing.entity.coupon.CartCouponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartCouponRepository extends JpaRepository<CartCouponEntity, String> {

    @Modifying
    @Query("delete from CartCouponEntity c where c.username = :username")
    void deleteByUsername(@Param("username") String username);
}
