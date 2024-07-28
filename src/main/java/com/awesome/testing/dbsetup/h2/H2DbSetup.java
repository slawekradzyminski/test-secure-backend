package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dbsetup.DbSetup;
import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
@RequiredArgsConstructor
public class H2DbSetup implements DbSetup {

    static final Faker FAKER = new Faker();

    private final BasicUsersSetup basicUsersSetup;

    public void setupData() {
        basicUsersSetup.setupUsers();
    }

}
