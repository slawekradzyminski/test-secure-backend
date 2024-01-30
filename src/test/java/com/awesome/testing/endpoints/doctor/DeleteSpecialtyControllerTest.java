package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class DeleteSpecialtyControllerTest extends AbstractSpecialtiesControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldDeleteSpecialty() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String name = RandomStringUtils.randomAlphanumeric(10);
        Integer id = createSpecialty(token, name);

        // when
        ResponseEntity<?> response =
                executeDelete(SPECIALTIES + "/" + id,
                        getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    public void shouldReturn404ForUnknown() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<?> response =
                executeDelete(SPECIALTIES + "/99999",
                        getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<?> response =
                executeDelete(SPECIALTIES + "/1",
                        getJsonOnlyHeaders());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
