package com.awesome.testing.endpoints.doctor;

import com.awesome.testing.dto.specialty.CreateSpecialtyDto;
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

public class EditSpecialtyControllerTest extends AbstractSpecialtiesControllerTest {

    @Test
    @SuppressWarnings("ConstantConditions")
    public void shouldEditSpecialty() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String name = RandomStringUtils.randomAlphanumeric(10);
        Integer id = createSpecialty(token, name);
        String newName = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<SpecialtyDto> response =
                executePut(SPECIALTIES + "/" + id,
                        CreateSpecialtyDto.builder().name(newName).build(),
                        getHeadersWith(token),
                        SpecialtyDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo(newName);
        assertThat(response.getBody().getId()).isEqualTo(id);
    }

    @Test
    public void shouldReturn404ForUnknown() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_DOCTOR));
        String token = registerAndThenLoginSavingToken(user);
        String newName = RandomStringUtils.randomAlphanumeric(10);

        // when
        ResponseEntity<?> response =
                executePut(SPECIALTIES + "/9999999",
                        CreateSpecialtyDto.builder().name(newName).build(),
                        getHeadersWith(token));

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    public void shouldReturn403AsUnauthorized() {
        // when
        ResponseEntity<?> response =
                executePut(SPECIALTIES + "/1",
                        CreateSpecialtyDto.builder().name("any").build(),
                        getJsonOnlyHeaders());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
