package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.doctor.CreateDoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class EditDoctorTypeControllerTest extends AbstractDoctorTypeControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldEditDoctorType() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String initialDoctorType = RandomStringUtils.randomAlphanumeric(10);
        Integer id = createDoctorType(token, initialDoctorType);
        String newDoctorType = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<DoctorTypeDto> response =
                executePut(DOCTOR_TYPES + "/" + id,
                        CreateDoctorTypeDto.builder().doctorType(newDoctorType).build(),
                        getHeadersWith(token),
                        DoctorTypeDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDoctorType()).isEqualTo(newDoctorType);
        assertThat(response.getBody().getId()).isEqualTo(id);
    }

    @Test
    public void shouldReturn404ForUnknown() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String newDoctorType = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<?> response =
                executePut(DOCTOR_TYPES + "/9999999",
                        CreateDoctorTypeDto.builder().doctorType(newDoctorType).build(),
                        getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<?> response =
                executePut(DOCTOR_TYPES + "/1",
                        CreateDoctorTypeDto.builder().doctorType("any").build(),
                        getJsonOnlyHeaders());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
