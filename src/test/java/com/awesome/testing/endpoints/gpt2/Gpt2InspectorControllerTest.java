package com.awesome.testing.endpoints.gpt2;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.gpt2.Gpt2InspectorStatusDto;
import com.awesome.testing.dto.gpt2.Gpt2TraceRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.service.gpt2.Gpt2InspectorService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Gpt2InspectorControllerTest extends DomainHelper {

    private static final String STATUS_ENDPOINT = "/api/v1/learning/gpt2/status";
    private static final String TRACE_ENDPOINT = "/api/v1/learning/gpt2/trace";

    @MockitoBean
    private Gpt2InspectorService inspectorService;

    @Autowired
    private ObjectMapper objectMapper;

    private String authToken;

    @BeforeAll
    void initAuthToken() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        authToken = getToken(user);
    }

    @Test
    void requiresAuthenticationForInspectorDiscovery() {
        ResponseEntity<String> response = executeGet(
                STATUS_ENDPOINT, getJsonOnlyHeaders(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void returnsInspectorAvailabilityToAnAuthenticatedLearner() {
        when(inspectorService.status()).thenReturn(Gpt2InspectorStatusDto.builder()
                .available(true)
                .mode("full-local")
                .message("Real GPT-2 activations are ready")
                .modelLabel("openai-community/gpt2")
                .build());

        ResponseEntity<Gpt2InspectorStatusDto> response = executeGet(
                STATUS_ENDPOINT, getHeadersWith(authToken), Gpt2InspectorStatusDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isAvailable()).isTrue();
        assertThat(response.getBody().getMode()).isEqualTo("full-local");
    }

    @Test
    void validatesTraceInputBeforeCallingTheInspector() {
        Gpt2TraceRequestDto request = Gpt2TraceRequestDto.builder()
                .prompt(" ")
                .layer(12)
                .head(-1)
                .selectedTokenIndex(32)
                .build();

        ResponseEntity<Map> response = executePost(
                TRACE_ENDPOINT, request, getHeadersWith(authToken), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsKeys("prompt", "layer", "head", "selectedTokenIndex");
    }

    @Test
    void forwardsAValidAuthenticatedTraceRequest() throws Exception {
        JsonNode trace = objectMapper.readTree("{\"source\":\"gpt2-live\",\"layer\":2}");
        when(inspectorService.trace(any(Gpt2TraceRequestDto.class))).thenReturn(trace);
        Gpt2TraceRequestDto request = Gpt2TraceRequestDto.builder()
                .prompt("The animal was too")
                .layer(2)
                .head(4)
                .selectedTokenIndex(3)
                .build();

        ResponseEntity<JsonNode> response = executePost(
                TRACE_ENDPOINT, request, getHeadersWith(authToken), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path("source").asString()).isEqualTo("gpt2-live");
    }
}
