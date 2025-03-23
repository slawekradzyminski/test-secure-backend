package com.awesome.testing.endpoints.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.traffic.TrafficInfoDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class TrafficControllerTest extends DomainHelper {

    private static final String API_TRAFFIC_INFO = "/api/traffic/info";

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldReturnTrafficInfoWhenAuthenticated() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        String description = "Connect to the WebSocket endpoint and subscribe to the topic to receive real-time HTTP traffic events";

        // when
        ResponseEntity<TrafficInfoDto> response = executeGet(
                API_TRAFFIC_INFO,
                getHeadersWith(token),
                TrafficInfoDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getWebSocketEndpoint()).isEqualTo("/ws-traffic");
        assertThat(response.getBody().getTopic()).isEqualTo("/topic/traffic");
        assertThat(response.getBody().getDescription()).isEqualTo(description);
    }

    @Test
    void shouldGet401WhenNotAuthenticated() {
        // when
        ResponseEntity<ErrorDto> response = executeGet(
                API_TRAFFIC_INFO,
                getJsonOnlyHeaders(),
                ErrorDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
} 