package com.awesome.testing.endpoints.traffic;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.traffic.TrafficLogEntryDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.factory.UserFactory;
import com.awesome.testing.repository.TrafficLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "app.traffic.obfuscate-authorization=true",
        "app.traffic.obfuscate-emails=true"
})
class TrafficControllerServerObfuscationTest extends DomainHelper {

    private static final String API_TRAFFIC_LOGS = "/api/v1/traffic/logs";

    @Autowired
    private TrafficLogRepository trafficLogRepository;

    @BeforeEach
    void setUp() {
        trafficLogRepository.deleteAll();
    }

    @Test
    void shouldObfuscateAuthorizationHeadersAndEmailsInTrafficLogs() {
        UserRegisterDto user = UserFactory.getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);

        executeGet("/api/v1/users/me", getHeadersWith(token), String.class);

        ResponseEntity<PageDto<TrafficLogEntryDto>> response = executeGet(
                API_TRAFFIC_LOGS + "?pathContains=/api/v1/users",
                getJsonOnlyHeaders(),
                new ParameterizedTypeReference<>() {}
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getContent()).isNotEmpty();

        TrafficLogEntryDto signUpEntry = response.getBody().getContent().stream()
                .filter(entry -> entry.getPath().equals(REGISTER_ENDPOINT))
                .findFirst()
                .orElseThrow();

        TrafficLogEntryDto meEntry = response.getBody().getContent().stream()
                .filter(entry -> entry.getPath().equals("/api/v1/users/me"))
                .findFirst()
                .orElseThrow();

        assertThat(signUpEntry.getRequestBody()).doesNotContain(user.getEmail());
        assertThat(signUpEntry.getRequestBody()).contains("***");
        assertThat(meEntry.getRequestHeaders()).doesNotContain(token);
        assertThat(meEntry.getRequestHeaders()).contains("Authorization");
        assertThat(meEntry.getRequestHeaders()).contains("***");
    }
}
