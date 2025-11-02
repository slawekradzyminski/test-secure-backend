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

public class GetSystemPromptControllerTest extends DomainHelper {

    private static final String SYSTEM_PROMPT_ENDPOINT = "/users/system-prompt";

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGetSystemPromptAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant.";
        SystemPromptDto systemPromptDto = new SystemPromptDto(systemPrompt);

        executePut(
                SYSTEM_PROMPT_ENDPOINT,
                systemPromptDto,
                getHeadersWith(token),
                SystemPromptDto.class);

        // when
        ResponseEntity<SystemPromptDto> response = executeGet(
                SYSTEM_PROMPT_ENDPOINT,
                getHeadersWith(token),
                SystemPromptDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        getToken(user);

        // when
        ResponseEntity<Map<String, String>> response = executeGet(
                SYSTEM_PROMPT_ENDPOINT,
                getJsonOnlyHeaders(),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
