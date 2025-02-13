package com.awesome.testing.fakedata;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Component
@Transactional
@RequiredArgsConstructor
@Profile("test")
public class TestSetupData {

    @Transactional
    public void setupData() {
        // Empty implementation for tests
        // Tests should set up their own data as needed
    }
} 