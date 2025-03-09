package com.awesome.testing.endpoints.embeddings;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.embeddings.*;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingsControllerTest extends DomainHelper {

    private static final String API_EMBEDDINGS = "/api/embeddings";
    private static final String API_EMBEDDINGS_ENDPOINT = API_EMBEDDINGS + "/embeddings";
    private static final String API_ATTENTION_ENDPOINT = API_EMBEDDINGS + "/attention";
    private static final String API_REDUCE_ENDPOINT = API_EMBEDDINGS + "/reduce";

    @Test
    void shouldReturn200WhenGettingEmbeddings() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        EmbeddingsRequestDto requestDto = EmbeddingsRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .build();

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
    void shouldReturn200WhenGettingAttention() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        AttentionRequestDto requestDto = AttentionRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .build();

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
    void shouldReturn200WhenReducingEmbeddings() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        ReduceRequestDto requestDto = ReduceRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .reductionMethod("pca")
                .nComponents(2)
                .build();

        // when
        ResponseEntity<ReduceResponseDto> response = executePost(
                API_REDUCE_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                ReduceResponseDto.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getTokens()).isNotEmpty();
        assertThat(response.getBody().getReducedEmbeddings()).isNotEmpty();
        assertThat(response.getBody().getModelName()).isEqualTo("gpt2");
    }

    @Test
    void shouldReturn400WhenTextIsBlankForEmbeddings() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        EmbeddingsRequestDto requestDto = EmbeddingsRequestDto.builder()
                .text("")
                .modelName("gpt2")
                .build();

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
    void shouldReturn400WhenTextIsBlankForAttention() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        AttentionRequestDto requestDto = AttentionRequestDto.builder()
                .text("")
                .modelName("gpt2")
                .build();

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
    void shouldReturn400WhenTextIsBlankForReduce() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);

        ReduceRequestDto requestDto = ReduceRequestDto.builder()
                .text("")
                .modelName("gpt2")
                .reductionMethod("pca")
                .nComponents(2)
                .build();

        // when
        ResponseEntity<Object> response = executePost(
                API_REDUCE_ENDPOINT,
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
        EmbeddingsRequestDto requestDto = EmbeddingsRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .build();

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
    void shouldReturn401WhenNoTokenProvidedForAttention() {
        // given
        AttentionRequestDto requestDto = AttentionRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .build();

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
    void shouldReturn401WhenNoTokenProvidedForReduce() {
        // given
        ReduceRequestDto requestDto = ReduceRequestDto.builder()
                .text("Hello, world!")
                .modelName("gpt2")
                .reductionMethod("pca")
                .nComponents(2)
                .build();

        // when
        ResponseEntity<Object> response = executePost(
                API_REDUCE_ENDPOINT,
                requestDto,
                null,
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
} 