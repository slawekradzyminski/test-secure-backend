package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetSpecialtiesControllerTest extends AbstractSpecialtiesControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldGetSpecialty() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String name = RandomStringUtils.randomAlphanumeric(10);
        Integer id = createSpecialty(token, name);

        // when
        ResponseEntity<SpecialtyDto> response =
                executeGet(SPECIALTIES + "/" + id,
                        getHeadersWith(token),
                        SpecialtyDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo(name);
    }

    @Test
    public void shouldReturn404ForUnknown() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);

        // when
        ResponseEntity<?> response =
                executeGet(SPECIALTIES + "/99999",
                        getHeadersWith(token),
                        Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<SpecialtyDto[]> response =
                executeGet(SPECIALTIES + "/1",
                        getJsonOnlyHeaders(),
                        SpecialtyDto[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
