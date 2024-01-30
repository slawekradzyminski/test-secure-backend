package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.specialty.CreateSpecialtyDto;
import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.dto.specialty.SpecialtyIdDto;

public class AbstractSpecialtiesControllerTest extends DomainHelper {

    protected static final String SPECIALTIES = "/specialties";

    @SuppressWarnings("ConstantConditions")
    protected Integer createSpecialty(String token, String name) {
        return executePost(
                SPECIALTIES,
                CreateSpecialtyDto.builder().name(name).build(),
                getHeadersWith(token),
                SpecialtyIdDto.class)
                .getBody()
                .getId();
    }

    @SuppressWarnings("ConstantConditions")
    protected String getSpecialty(String token, Integer id) {
        return executeGet(SPECIALTIES + "/" + id,
                        getHeadersWith(token),
                        SpecialtyDto.class)
                        .getBody()
                        .getName();
    }

}
