package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.dto.user.Role.ROLE_CLIENT;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

/** T-028: PUT /users/{username} answers with UserResponseDto, not the persistence entity. */
class EditUserResponseShapeTest extends DomainHelper {

    @Test
    void shouldAnswerWithTheUserResponseFieldsOnly() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(ROLE_CLIENT));
        String token = getToken(user);

        // when
        ResponseEntity<Map> response = executePut(getUserEndpoint(user.getUsername()),
                Map.of("email", user.getEmail(), "firstName", "Edited"), getHeadersWith(token), Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnlyKeys("id", "username", "email", "roles", "firstName", "lastName")
                .containsEntry("username", user.getUsername())
                .containsEntry("firstName", "Edited");
    }
}
