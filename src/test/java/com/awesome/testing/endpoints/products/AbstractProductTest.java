package com.awesome.testing.endpoints.products;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

public abstract class AbstractProductTest extends DomainHelper {

    protected static final String PRODUCTS_ENDPOINT = "/api/products";

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected CartItemRepository cartItemRepository;

    @Autowired
    protected OrderRepository orderRepository;

    @BeforeEach
    @Transactional
    public void setUp() {
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
    }

}