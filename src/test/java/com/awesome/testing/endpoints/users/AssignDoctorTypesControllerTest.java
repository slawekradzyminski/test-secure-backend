package com.awesome.testing.endpoints.users;

import com.awesome.testing.dto.doctor.DoctorTypeDto;
import com.awesome.testing.dto.doctor.DoctorTypeUpdateDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.endpoints.doctor.AbstractDoctorTypeControllerTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.util.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class AssignDoctorTypesControllerTest extends AbstractDoctorTypeControllerTest {

    @Test
    public void shouldAssignAllExistingDoctorTypes() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        String doctorType1 = RandomStringUtils.randomAlphanumeric(10);
        String doctorType2 = RandomStringUtils.randomAlphanumeric(10);
        Integer id1 = createDoctorType(token, doctorType1);
        Integer id2 = createDoctorType(token, doctorType2);

        // when
        ResponseEntity<UserResponseDto> response =
                executePut(USERS_ENDPOINT + "/doctortypes",
                        new DoctorTypeUpdateDto(List.of(id1, id2)),
                        getHeadersWith(token),
                        UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDoctorTypes()).containsExactlyInAnyOrder(
                DoctorTypeDto.builder().id(id1).doctorType(doctorType1).build(),
                DoctorTypeDto.builder().id(id2).doctorType(doctorType2).build()
        );
    }

    @Test
    public void shouldAssignSingleExistingDoctorTypes() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        String doctorType1 = RandomStringUtils.randomAlphanumeric(10);
        Integer id1 = createDoctorType(token, doctorType1);

        // when
        ResponseEntity<UserResponseDto> response =
                executePut(USERS_ENDPOINT + "/doctortypes",
                        new DoctorTypeUpdateDto(List.of(id1, 999999)),
                        getHeadersWith(token),
                        UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getDoctorTypes()).containsExactlyInAnyOrder(
                DoctorTypeDto.builder().id(id1).doctorType(doctorType1).build()
        );
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<?> response =
                executePut(USERS_ENDPOINT + "/doctortypes",
                        new DoctorTypeUpdateDto(List.of(1, 999999)),
                        getJsonOnlyHeaders());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
