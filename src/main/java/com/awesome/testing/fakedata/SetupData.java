package com.awesome.testing.fakedata;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Component
@Transactional
@RequiredArgsConstructor
public class SetupData {

    private final SetupUsers setupUsers;
    private final SetupProducts setupProducts;

    @Transactional
    public void setupData() {
        setupUsers.createUsers();
        setupProducts.createProducts();
    }

}
