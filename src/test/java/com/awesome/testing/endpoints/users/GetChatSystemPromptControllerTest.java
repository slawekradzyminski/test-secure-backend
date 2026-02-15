package com.awesome.testing.endpoints.users;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.systemprompt.ChatSystemPromptDto;
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

class GetChatSystemPromptControllerTest extends DomainHelper {

    private static final String CHAT_SYSTEM_PROMPT_ENDPOINT = "/users/chat-system-prompt";

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldGetChatSystemPromptAsAdmin() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant.";
        ChatSystemPromptDto dto = new ChatSystemPromptDto(systemPrompt);

        executePut(
                CHAT_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                ChatSystemPromptDto.class);

        ResponseEntity<ChatSystemPromptDto> response = executeGet(
                CHAT_SYSTEM_PROMPT_ENDPOINT,
                getHeadersWith(token),
                ChatSystemPromptDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getChatSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    void shouldGet401AsUnauthorized() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        getToken(user);

        ResponseEntity<Map<String, String>> response = executeGet(
                CHAT_SYSTEM_PROMPT_ENDPOINT,
                getJsonOnlyHeaders(),
                mapTypeReference());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
