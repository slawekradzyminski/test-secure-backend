package com.awesome.testing.dbsetup;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DbInitialDataSetup {

    private final UsersSetup usersSetup;
    private final DoctorTypesSetup doctorTypesSetup;

    public void setupData() {
        usersSetup.setupUsers();
        doctorTypesSetup.setupDoctorTypes();
    }



}
