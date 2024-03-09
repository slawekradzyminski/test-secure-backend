package com.awesome.testing.dbsetup;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class MySqlDbSetup implements DbSetup {

    @Override
    public void setupData() {
        // nothing
    }

}
