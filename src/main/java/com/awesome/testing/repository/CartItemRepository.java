package com.awesome.testing.repository;

import com.awesome.testing.model.CartItem;
import com.awesome.testing.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByUser(User user);
    Optional<CartItem> findByUserAndProduct_Id(User user, Long productId);
    void deleteByUser(User user);
} 