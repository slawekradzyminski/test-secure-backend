package com.awesome.testing.endpoints;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static com.awesome.testing.factory.ProductFactory.getRandomProduct;

public abstract class AbstractEcommerceTest extends DomainHelper {

    protected static final String PRODUCTS_ENDPOINT = "/api/products";
    protected static final String ORDERS_ENDPOINT = "/api/orders";
    protected static final String CART_ITEMS_ENDPOINT = "/api/cart/items";
    protected static final String CART_ENDPOINT = "/api/cart";

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

    protected ProductEntity setupProduct() {
        ProductEntity testProduct = getRandomProduct();
        return productRepository.save(testProduct);
    }

}