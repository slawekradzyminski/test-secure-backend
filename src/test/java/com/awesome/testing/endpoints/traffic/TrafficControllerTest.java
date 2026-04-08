package com.awesome.testing.endpoints.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.traffic.TrafficInfoDto;
import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.factory.UserFactory;
import com.awesome.testing.repository.TrafficLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class TrafficControllerTest extends DomainHelper {

    private static final String API_TRAFFIC_INFO = "/api/v1/traffic/info";
    private static final String API_TRAFFIC_LOGS = "/api/v1/traffic/logs";

    @Autowired
    private TrafficLogRepository trafficLogRepository;

    @BeforeEach
    void setUp() {
        trafficLogRepository.deleteAll();
    }

    @Test
    void shouldReturnTrafficInfoWithoutAuthentication() {
        ResponseEntity<TrafficInfoDto> response = executeGet(
                API_TRAFFIC_INFO,
                getJsonOnlyHeaders(),
                TrafficInfoDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getWebSocketEndpoint()).isEqualTo("/api/v1/ws-traffic");
        assertThat(response.getBody().getTopic()).isEqualTo("/topic/traffic");
    }

    @Test
    void shouldReturnEmptyTrafficLogsPageWithoutAuthentication() {
        ResponseEntity<PageDto<TrafficLogEntryDto>> response = executeGet(
                API_TRAFFIC_LOGS,
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isEmpty();
        assertThat(response.getBody().getTotalElements()).isZero();
    }

    @Test
    void shouldReturnPaginatedTrafficLogsAndExcludeTrafficEndpointRequests() {
        UserRegisterDto user = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(user);

        executePost(
                LOGIN_ENDPOINT,
                LoginDto.builder()
                        .username(user.getUsername())
                        .password(user.getPassword())
                        .build(),
                getJsonOnlyHeaders(),
                LoginResponseDto.class
        );

        ResponseEntity<PageDto<TrafficLogEntryDto>> response = executeGet(
                API_TRAFFIC_LOGS + "?page=0&size=5&pathContains=/api/v1/users",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isNotEmpty();
        assertThat(response.getBody().getContent())
                .allMatch(entry -> entry.getPath().startsWith("/api/v1/users"));
        assertThat(response.getBody().getContent())
                .noneMatch(entry -> entry.getPath().startsWith(API_TRAFFIC_LOGS));
        assertThat(response.getBody().getTotalElements()).isGreaterThanOrEqualTo(2);
    }
}
