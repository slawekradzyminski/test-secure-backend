package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.systemprompt.ToolSystemPromptDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class GetToolSystemPromptControllerTest extends DomainHelper {

    private static final String TOOL_SYSTEM_PROMPT_ENDPOINT = "/users/tool-system-prompt";

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldGetToolSystemPromptAsAdmin() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);
        String systemPrompt = "Call get_product_snapshot first.";
        ToolSystemPromptDto dto = new ToolSystemPromptDto(systemPrompt);

        executePut(
                TOOL_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                ToolSystemPromptDto.class);

        ResponseEntity<ToolSystemPromptDto> response = executeGet(
                TOOL_SYSTEM_PROMPT_ENDPOINT,
                getHeadersWith(token),
                ToolSystemPromptDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToolSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    void shouldGet401AsUnauthorized() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        getToken(user);

        ResponseEntity<Map<String, String>> response = executeGet(
                TOOL_SYSTEM_PROMPT_ENDPOINT,
                getJsonOnlyHeaders(),
                mapTypeReference());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
