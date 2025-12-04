package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.systemprompt.SystemPromptDto;
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

public class UpdateSystemPromptControllerTest extends DomainHelper {

    private static final String SYSTEM_PROMPT_ENDPOINT = "/users/system-prompt";
    private static final String SYSTEM_PROMPT_TOO_LONG = "A".repeat(5001);

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateSystemPromptAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant.";
        SystemPromptDto systemPromptDto = new SystemPromptDto(systemPrompt);

        // when
        ResponseEntity<SystemPromptDto> response = executePut(
                SYSTEM_PROMPT_ENDPOINT,
                systemPromptDto,
                getHeadersWith(token),
                SystemPromptDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSystemPrompt()).isEqualTo(systemPrompt);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldUpdateSystemPromptAsClient() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant for a client.";
        SystemPromptDto systemPromptDto = new SystemPromptDto(systemPrompt);

        // when
        ResponseEntity<SystemPromptDto> response = executePut(
                SYSTEM_PROMPT_ENDPOINT,
                systemPromptDto,
                getHeadersWith(token),
                SystemPromptDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    public void shouldGet400WhenSystemPromptTooLong() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        SystemPromptDto systemPromptDto = new SystemPromptDto(SYSTEM_PROMPT_TOO_LONG);

        // when
        ResponseEntity<Map<String, String>> response = executePut(
                SYSTEM_PROMPT_ENDPOINT,
                systemPromptDto,
                getHeadersWith(token),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
