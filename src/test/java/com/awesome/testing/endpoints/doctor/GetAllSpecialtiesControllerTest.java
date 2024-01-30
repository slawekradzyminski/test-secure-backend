package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetAllSpecialtiesControllerTest extends AbstractSpecialtiesControllerTest {

    @Test
    public void shouldGetSpecialties() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String doctorToken = registerAndThenLoginSavingToken(user);
        createSpecialty(doctorToken, "Physiotherapist");

        // when
        ResponseEntity<SpecialtyDto[]> response =
                executeGet(SPECIALTIES,
                        getHeadersWith(doctorToken),
                        SpecialtyDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSizeGreaterThan(0);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<SpecialtyDto[]> response =
                executeGet(SPECIALTIES,
                        getJsonOnlyHeaders(),
                        SpecialtyDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
