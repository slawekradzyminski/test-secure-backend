package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDTO;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class DeleteDoctorTypeControllerTest extends AbstractDoctorTypeControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldDeleteDoctorType() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String doctorType = RandomStringUtils.randomAlphanumeric(10);
        Integer id = createDoctorType(token, doctorType);

        // when
        ResponseEntity<?> response =
                executeDelete(DOCTOR_TYPES + "/" + id,
                        getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    public void shouldReturn404ForUnknown() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<?> response =
                executeDelete(DOCTOR_TYPES + "/99999",
                        getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<?> response =
                executeDelete(DOCTOR_TYPES + "/1",
                        getJsonOnlyHeaders());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
