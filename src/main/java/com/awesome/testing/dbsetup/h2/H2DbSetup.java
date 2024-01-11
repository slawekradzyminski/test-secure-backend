package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dbsetup.DbSetup;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("dev")
@Component
@RequiredArgsConstructor
public class H2DbSetup implements DbSetup {

    private final UsersSetup usersSetup;
    private final DoctorTypesSetup doctorTypesSetup;
    private final AssignDoctorTypesSetup assignDoctorTypesSetup;
    private final DoctorSlotsSetup doctorSlotsSetup;

    public void setupData() {
        usersSetup.setupUsers();
        doctorTypesSetup.setupDoctorTypes();
        assignDoctorTypesSetup.assignDoctorTypesForDoctor();
        doctorSlotsSetup.setupSlotsForMonthsAhead();
    }

}
