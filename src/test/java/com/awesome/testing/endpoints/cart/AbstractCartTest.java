package com.awesome.testing.endpoints.cart;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public abstract class AbstractCartTest extends DomainHelper {

    protected static final String CART_ENDPOINT = "/api/cart";

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected CartItemRepository cartItemRepository;

    @BeforeEach
    @Transactional
    public void setUp() {
        cartItemRepository.deleteAll();
        productRepository.deleteAll();
    }

}