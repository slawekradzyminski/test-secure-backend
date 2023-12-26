package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDTO;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetDoctorTypeControllerTest extends AbstractDoctorTypeControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldGetDoctorType() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String doctorType = RandomStringUtils.randomAlphanumeric(10);
        Integer id = createDoctorType(token, doctorType);

        // when
        ResponseEntity<DoctorTypeDto> response =
                executeGet(DOCTOR_TYPES + "/" + id,
                        getHeadersWith(token),
                        DoctorTypeDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDoctorType()).isEqualTo(doctorType);
    }

    @Test
    public void shouldReturn404ForUnknown() {
        // when
        ResponseEntity<DoctorTypeDto[]> response =
                executeGet(DOCTOR_TYPES + "/1",
                        getJsonOnlyHeaders(),
                        DoctorTypeDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<DoctorTypeDto[]> response =
                executeGet(DOCTOR_TYPES + "/1",
                        getJsonOnlyHeaders(),
                        DoctorTypeDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
