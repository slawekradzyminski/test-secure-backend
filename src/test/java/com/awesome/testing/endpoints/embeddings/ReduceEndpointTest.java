package com.awesome.testing.endpoints.embeddings;

import com.awesome.testing.dto.embeddings.ReduceResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.EmbeddingsFactory.createInvalidReduceRequest;
import static com.awesome.testing.factory.EmbeddingsFactory.createValidReduceRequest;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class ReduceEndpointTest extends AbstractEmbeddingsTest {

    @Test
    void shouldReturn200WhenGettingReduce() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createValidReduceRequest();
        stubReduceSuccess();

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
        assertThat(response.getBody().getReducedEmbeddings()).isNotEmpty();
        assertThat(response.getBody().getModelName()).isEqualTo("gpt2");
    }

    @Test
    void shouldReturn400WhenEmbeddingsIsEmptyForReduce() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createInvalidReduceRequest();

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
    void shouldReturn401WhenNoTokenProvidedForReduce() {
        // given
        var requestDto = createValidReduceRequest();

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
    
    @Test
    void shouldReturn500WhenSidecarFailsForReduce() {
        // given
        UserRegisterDto clientDto = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(clientDto);
        var requestDto = createValidReduceRequest();
        stubReduceServerError();

        // when
        ResponseEntity<Object> response = executePost(
                API_REDUCE_ENDPOINT,
                requestDto,
                getHeadersWith(clientToken),
                Object.class
        );

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
} 