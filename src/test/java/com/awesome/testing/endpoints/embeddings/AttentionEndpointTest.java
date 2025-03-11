package com.awesome.testing.endpoints.embeddings;

import com.awesome.testing.dto.embeddings.AttentionResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.EmbeddingsFactory.createInvalidAttentionRequest;
import static com.awesome.testing.factory.EmbeddingsFactory.createValidAttentionRequest;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class AttentionEndpointTest extends AbstractEmbeddingsTest {

    @Test
    void shouldReturn200WhenGettingAttention() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createValidAttentionRequest();
        stubAttentionSuccess();

        // when
        ResponseEntity<AttentionResponseDto> response = executePost(
                API_ATTENTION_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                AttentionResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTokens()).isNotEmpty();
        assertThat(response.getBody().getAttention()).isNotEmpty();
        assertThat(response.getBody().getModelName()).isEqualTo("gpt2");
    }

    @Test
    void shouldReturn400WhenTextIsBlankForAttention() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createInvalidAttentionRequest();

        // when
        ResponseEntity<Object> response = executePost(
                API_ATTENTION_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401WhenNoTokenProvidedForAttention() {
        // given
        var requestDto = createValidAttentionRequest();

        // when
        ResponseEntity<Object> response = executePost(
                API_ATTENTION_ENDPOINT,
                requestDto,
                null,
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    void shouldReturn500WhenSidecarFailsForAttention() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createValidAttentionRequest();
        stubAttentionServerError();

        // when
        ResponseEntity<Object> response = executePost(
                API_ATTENTION_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 