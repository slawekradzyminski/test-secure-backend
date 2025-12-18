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

public class UpdateToolSystemPromptControllerTest extends DomainHelper {

    private static final String TOOL_SYSTEM_PROMPT_ENDPOINT = "/users/tool-system-prompt";
    private static final String TOOL_PROMPT_TOO_LONG = "B".repeat(5001);

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateToolSystemPromptAsAdmin() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);
        String systemPrompt = "You must call list_products first.";
        ToolSystemPromptDto dto = new ToolSystemPromptDto(systemPrompt);

        ResponseEntity<ToolSystemPromptDto> response = executePut(
                TOOL_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                ToolSystemPromptDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToolSystemPrompt()).isEqualTo(systemPrompt);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateToolSystemPromptAsClient() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        String systemPrompt = "Client tool prompt.";
        ToolSystemPromptDto dto = new ToolSystemPromptDto(systemPrompt);

        ResponseEntity<ToolSystemPromptDto> response = executePut(
                TOOL_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                ToolSystemPromptDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToolSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    public void shouldGet400WhenToolSystemPromptTooLong() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        ToolSystemPromptDto dto = new ToolSystemPromptDto(TOOL_PROMPT_TOO_LONG);

        ResponseEntity<Map<String, String>> response = executePut(
                TOOL_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                mapTypeReference());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
