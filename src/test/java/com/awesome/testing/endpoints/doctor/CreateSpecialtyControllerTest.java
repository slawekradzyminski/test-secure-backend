package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.specialty.CreateSpecialtyDto;
import com.awesome.testing.dto.specialty.SpecialtyIdDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateSpecialtyControllerTest extends AbstractSpecialtiesControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldCreateSpecialty() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String name = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<SpecialtyIdDto> response = executePost(
                SPECIALTIES,
                CreateSpecialtyDto.builder().name(name).build(),
                getHeadersWith(token),
                SpecialtyIdDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getId()).isPositive();
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // given
        String name = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<?> response = executePost(
                SPECIALTIES,
                CreateSpecialtyDto.builder().name(name).build(),
                getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
