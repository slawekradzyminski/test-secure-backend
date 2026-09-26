package com.awesome.testing.endpoints.qr;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.qr.CreateQrDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.text.MessageFormat;
import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class CreateQrControllerTest extends DomainHelper {

    private final String CREATE_QR_CODE_ENDPOINT = "/api/v1/qr/create";

    @SneakyThrows
    @Test
    void shouldReturnPngAsAdmin() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = getToken(admin);
        CreateQrDto createQrDto = new CreateQrDto("https://www.awesome-testing.com");

        // when
        ResponseEntity<byte[]> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeadersWith(apiToken), byte[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isNotEmpty();
        var image = ImageIO.read(new ByteArrayInputStream(response.getBody()));
        assertThat(image).isNotNull();
    }

    @Test
    void shouldGet400WhenTextIsEmpty() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = getToken(admin);
        CreateQrDto createQrDto = new CreateQrDto("");

        // when
        ResponseEntity<byte[]> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeadersWith(apiToken), byte[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void shouldGet401AsUnauthorized() {
        // given
        CreateQrDto createQrDto = new CreateQrDto("test");

        // when
        ResponseEntity<?> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeaders(), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    protected HttpHeaders getImageHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.IMAGE_PNG_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    private HttpHeaders getImageHeadersWith(String apiToken) {
        HttpHeaders headers = getImageHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, MessageFormat.format("Bearer {0}", apiToken));
        return headers;
    }

}
