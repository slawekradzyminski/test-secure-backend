package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.doctor.CreateDoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeIdDto;

public class AbstractDoctorTypeControllerTest extends DomainHelper {

    protected static final String DOCTOR_TYPES = "/doctortypes";

    @SuppressWarnings("ConstantConditions")
    protected Integer createDoctorType(String token, String doctorType) {
        return executePost(
                DOCTOR_TYPES,
                CreateDoctorTypeDto.builder().doctorType(doctorType).build(),
                getHeadersWith(token),
                DoctorTypeIdDto.class)
                .getBody()
                .getId();
    }

    @SuppressWarnings("ConstantConditions")
    protected String getDoctorType(String token, Integer id) {
        return executeGet(DOCTOR_TYPES + "/" + id,
                        getHeadersWith(token),
                        DoctorTypeDto.class)
                        .getBody()
                        .getDoctorType();
    }

}
