package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.doctor.CreateDoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeIdDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDTO;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateDoctorTypeControllerTest extends AbstractDoctorTypeControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldCreateDoctorType() {
        // given
        UserRegisterDTO user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String doctorType = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<DoctorTypeIdDto> response = executePost(
                DOCTOR_TYPES,
                CreateDoctorTypeDto.builder().doctorType(doctorType).build(),
                getHeadersWith(token),
                DoctorTypeIdDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isPositive();
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // given
        String doctorType = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<?> response = executePost(
                DOCTOR_TYPES,
                CreateDoctorTypeDto.builder().doctorType(doctorType).build(),
                getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
