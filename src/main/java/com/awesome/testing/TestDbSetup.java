package com.awesome.testing;

import com.awesome.testing.dbsetup.DbSetup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * speeds up testing as dev profile setups a lot of data
 */
@Component
@Profile("test")
public class TestDbSetup implements DbSetup {
    @Override
    public void setupData() {

    }
}
