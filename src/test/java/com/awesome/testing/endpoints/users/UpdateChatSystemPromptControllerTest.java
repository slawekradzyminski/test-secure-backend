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

class UpdateChatSystemPromptControllerTest extends DomainHelper {

    private static final String CHAT_SYSTEM_PROMPT_ENDPOINT = "/users/chat-system-prompt";
    private static final String SYSTEM_PROMPT_TOO_LONG = "A".repeat(5001);

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldUpdateChatSystemPromptAsAdmin() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant.";
        ChatSystemPromptDto dto = new ChatSystemPromptDto(systemPrompt);

        ResponseEntity<ChatSystemPromptDto> response = executePut(
                CHAT_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                ChatSystemPromptDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getChatSystemPrompt()).isEqualTo(systemPrompt);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldUpdateChatSystemPromptAsClient() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant for a client.";
        ChatSystemPromptDto dto = new ChatSystemPromptDto(systemPrompt);

        ResponseEntity<ChatSystemPromptDto> response = executePut(
                CHAT_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                ChatSystemPromptDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getChatSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    void shouldGet400WhenChatSystemPromptTooLong() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        ChatSystemPromptDto dto = new ChatSystemPromptDto(SYSTEM_PROMPT_TOO_LONG);

        ResponseEntity<Map<String, String>> response = executePut(
                CHAT_SYSTEM_PROMPT_ENDPOINT,
                dto,
                getHeadersWith(token),
                mapTypeReference());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
