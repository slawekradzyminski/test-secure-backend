package com.awesome.testing.endpoints.conversation;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.conversation.CreateConversationRequestDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractConversationControllerTest extends DomainHelper {

    protected static final String CONVERSATIONS_ENDPOINT = "/api/ollama/conversations";

    protected String createClientToken() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        assertThat(token).isNotBlank();
        return token;
    }

    protected ConversationDetailDto createConversation(String token, ConversationType type) {
        ResponseEntity<ConversationDetailDto> response = executePost(
                CONVERSATIONS_ENDPOINT,
                CreateConversationRequestDto.builder().type(type).build(),
                getHeadersWith(token),
                ConversationDetailDto.class
        );
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }
}
