package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetDoctorTypesControllerTest extends AbstractDoctorTypeControllerTest {

    @Test
    public void shouldGetDoctorTypes() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String doctorToken = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<DoctorTypeDto[]> response =
                executeGet(DOCTOR_TYPES,
                        getHeadersWith(doctorToken),
                        DoctorTypeDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThan(0);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<DoctorTypeDto[]> response =
                executeGet(DOCTOR_TYPES,
                        getJsonOnlyHeaders(),
                        DoctorTypeDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
