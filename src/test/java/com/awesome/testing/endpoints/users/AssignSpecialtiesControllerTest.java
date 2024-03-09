package com.awesome.testing.endpoints.users;

import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.dto.specialty.SpecialtiesUpdateDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.endpoints.doctor.AbstractSpecialtiesControllerTest;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class AssignSpecialtiesControllerTest extends AbstractSpecialtiesControllerTest {

    @Test
    public void shouldAssignAllExistingSpecialties() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        String specialty1 = RandomStringUtils.randomAlphanumeric(10);
        String specialty2 = RandomStringUtils.randomAlphanumeric(10);
        Integer id1 = createSpecialty(token, specialty1);
        Integer id2 = createSpecialty(token, specialty2);

        // when
        ResponseEntity<UserResponseDto> response =
                executePut(USERS_ENDPOINT + "/specialties",
                        new SpecialtiesUpdateDto(List.of(id1, id2)),
                        getHeadersWith(token),
                        UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSpecialties()).containsExactlyInAnyOrder(
                SpecialtyDto.builder().id(id1).name(specialty1).build(),
                SpecialtyDto.builder().id(id2).name(specialty2).build()
        );
    }

    @Test
    public void shouldAssignSingleExistingSpecialties() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = registerAndThenLoginSavingToken(user);
        String name = RandomStringUtils.randomAlphanumeric(10);
        Integer id = createSpecialty(token, name);

        // when
        ResponseEntity<UserResponseDto> response =
                executePut(USERS_ENDPOINT + "/specialties",
                        new SpecialtiesUpdateDto(List.of(id, 999999)),
                        getHeadersWith(token),
                        UserResponseDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSpecialties()).containsExactlyInAnyOrder(
                SpecialtyDto.builder().id(id).name(name).build()
        );
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // when
        ResponseEntity<?> response =
                executePut(USERS_ENDPOINT + "/specialties",
                        new SpecialtiesUpdateDto(List.of(1, 999999)),
                        getJsonOnlyHeaders());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
