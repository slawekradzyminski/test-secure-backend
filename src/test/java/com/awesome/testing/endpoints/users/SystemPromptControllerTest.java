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

public class SystemPromptControllerTest extends DomainHelper {

    private static final String SYSTEM_PROMPT_ENDPOINT = "/system-prompt";
    
    // Create a string that exceeds 500 characters for testing validation
    private static final String SYSTEM_PROMPT_TOO_LONG = "A".repeat(501);

    @Test
    public void shouldUpdateSystemPromptAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant.";
        SystemPromptDto systemPromptDto = new SystemPromptDto(systemPrompt);

        // when
        ResponseEntity<SystemPromptDto> response = executePut(
                getUserEndpoint(username) + SYSTEM_PROMPT_ENDPOINT,
                systemPromptDto,
                getHeadersWith(token),
                SystemPromptDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    public void shouldGetSystemPromptAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String username = user.getUsername();
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant.";
        SystemPromptDto systemPromptDto = new SystemPromptDto(systemPrompt);

        // Update system prompt first
        executePut(
                getUserEndpoint(username) + SYSTEM_PROMPT_ENDPOINT,
                systemPromptDto,
                getHeadersWith(token),
                SystemPromptDto.class);

        // when
        ResponseEntity<SystemPromptDto> response = executeGet(
                getUserEndpoint(username) + SYSTEM_PROMPT_ENDPOINT,
                getHeadersWith(token),
                SystemPromptDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getSystemPrompt()).isEqualTo(systemPrompt);
    }

    @Test
    public void shouldUpdateSystemPromptAsClient() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        String token = getToken(user);
        String systemPrompt = "You are a helpful assistant for a client.";
        SystemPromptDto systemPromptDto = new SystemPromptDto(systemPrompt);

        // when
        ResponseEntity<SystemPromptDto> response = executePut(
                getUserEndpoint(username) + SYSTEM_PROMPT_ENDPOINT,
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
        String username = user.getUsername();
        String token = getToken(user);
        SystemPromptDto systemPromptDto = new SystemPromptDto(SYSTEM_PROMPT_TOO_LONG);

        // when
        ResponseEntity<Map<String, String>> response = executePut(
                getUserEndpoint(username) + SYSTEM_PROMPT_ENDPOINT,
                systemPromptDto,
                getHeadersWith(token),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username = user.getUsername();
        getToken(user); // Register the user but don't use the token

        // when
        ResponseEntity<Map<String, String>> response = executeGet(
                getUserEndpoint(username) + SYSTEM_PROMPT_ENDPOINT,
                getJsonOnlyHeaders(),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldGet403AsClientAccessingOtherUser() {
        // given
        UserRegisterDto user1 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        UserRegisterDto user2 = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String username1 = user1.getUsername();
        String username2 = user2.getUsername();
        String token1 = getToken(user1);
        getToken(user2); // Register user2

        // when
        ResponseEntity<Map<String, String>> response = executeGet(
                getUserEndpoint(username2) + SYSTEM_PROMPT_ENDPOINT,
                getHeadersWith(token1),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet404ForNonExistingUser() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        String nonExistingUsername = "nonexistinguser";

        // when
        ResponseEntity<Map<String, String>> response = executeGet(
                getUserEndpoint(nonExistingUsername) + SYSTEM_PROMPT_ENDPOINT,
                getHeadersWith(adminToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

} 