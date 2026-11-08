package com.awesome.testing.fakedata;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Component
@Transactional
@RequiredArgsConstructor
@Profile("!test")
public class SetupData {

    private final SetupUsers setupUsers;
    private final SetupProducts setupProducts;
    private final SetupOrders setupOrders;
    private final SetupCommerce setupCommerce;

    // Checked at runtime, not with @ConditionalOnProperty: native images fix conditions at build time.
    @org.springframework.beans.factory.annotation.Value("${app.seed-demo-data.enabled:false}")
    private boolean enabled;

    @Transactional
    public void setupData() {
        if (!enabled) {
            return;
        }
        setupUsers.createUsers();
        setupProducts.normalizeProductImageUrls();
        setupProducts.createProducts();
        setupOrders.createOrdersAndCart();
        setupCommerce.createCoupons();
    }

}
