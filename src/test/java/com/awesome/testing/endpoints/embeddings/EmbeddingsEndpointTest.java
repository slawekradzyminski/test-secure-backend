package com.awesome.testing.endpoints.embeddings;

import com.awesome.testing.dto.embeddings.EmbeddingsResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.EmbeddingsFactory.createInvalidEmbeddingsRequest;
import static com.awesome.testing.factory.EmbeddingsFactory.createValidEmbeddingsRequest;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingsEndpointTest extends AbstractEmbeddingsTest {

    @Test
    void shouldReturn200WhenGettingEmbeddings() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createValidEmbeddingsRequest();
        stubEmbeddingsSuccess();

        // when
        ResponseEntity<EmbeddingsResponseDto> response = executePost(
                API_EMBEDDINGS_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                EmbeddingsResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTokens()).isNotEmpty();
        assertThat(response.getBody().getEmbeddings()).isNotEmpty();
        assertThat(response.getBody().getModelName()).isEqualTo("gpt2");
    }

    @Test
    void shouldReturn400WhenTextIsBlankForEmbeddings() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createInvalidEmbeddingsRequest();

        // when
        ResponseEntity<Object> response = executePost(
                API_EMBEDDINGS_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldReturn401WhenNoTokenProvidedForEmbeddings() {
        // given
        var requestDto = createValidEmbeddingsRequest();

        // when
        ResponseEntity<Object> response = executePost(
                API_EMBEDDINGS_ENDPOINT,
                requestDto,
                null,
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    void shouldReturn500WhenSidecarFailsForEmbeddings() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createValidEmbeddingsRequest();
        
        stubEmbeddingsServerError();

        // when
        ResponseEntity<Object> response = executePost(
                API_EMBEDDINGS_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 