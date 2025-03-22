package com.awesome.testing.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class TrafficControllerTest extends DomainHelper {

    @Test
    void shouldReturnTrafficInfoWhenAuthenticated() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);

        // when
        ResponseEntity<Map> response = executeGet(
                "/api/traffic/info",
                getHeadersWith(token),
                Map.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("webSocketEndpoint")).isEqualTo("/ws-traffic");
        assertThat(response.getBody().get("topic")).isEqualTo("/topic/traffic");
        assertThat(response.getBody().get("description")).isNotNull();
    }

    @Test
    void shouldGet401WhenNotAuthenticated() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(
                "/api/traffic/info",
                getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
} 