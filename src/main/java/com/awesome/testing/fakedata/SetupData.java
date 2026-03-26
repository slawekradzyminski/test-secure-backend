package com.awesome.testing.fakedata;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Component
@Transactional
@RequiredArgsConstructor
@Profile("!test") // Active in all profiles except test
public class SetupData {

    private final SetupUsers setupUsers;
    private final SetupProducts setupProducts;
    private final SetupOrders setupOrders;

    @Transactional
    public void setupData() {
        setupUsers.createUsers();
        setupProducts.normalizeProductImageUrls();
        setupProducts.createProducts();
        setupOrders.createOrdersAndCart();
    }

}
