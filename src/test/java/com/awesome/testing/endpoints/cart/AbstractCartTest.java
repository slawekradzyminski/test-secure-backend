package com.awesome.testing.endpoints.cart;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractCartTest extends DomainHelper {

    protected static final String CART_ENDPOINT = "/api/cart";

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected CartItemRepository cartItemRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @BeforeEach
    public void setUp() {
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

}