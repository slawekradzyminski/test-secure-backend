package com.awesome.testing.fakedata;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SetupData {

    private final SetupUsers setupUsers;
    private final SetupProducts setupProducts;

    public void setupData() {
        setupUsers.createUsers();
        setupProducts.createProducts();
    }

}
