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
    void shouldGet400WithAFieldErrorWhenTextDoesNotFitInAQrCode() {
        // given
        String apiToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        CreateQrDto createQrDto = new CreateQrDto("a".repeat(2954));

        // when
        ResponseEntity<String> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeadersWith(apiToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("{\"text\":\"Text is too long for a QR code\"}");
    }

    @Test
    void shouldEncodePolishLettersAndEmojiExactly() throws Exception {
        // given
        String apiToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        String text = "Zażółć gęślą jaźń 😀";

        // when
        ResponseEntity<byte[]> response = executePost(CREATE_QR_CODE_ENDPOINT, new CreateQrDto(text),
                getImageHeadersWith(apiToken), byte[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var image = ImageIO.read(new ByteArrayInputStream(response.getBody()));
        var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));
        assertThat(new com.google.zxing.MultiFormatReader().decode(bitmap).getText()).isEqualTo(text);
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

    @Test
    void shouldReportValidationErrorsAs400JsonWhenTheClientAcceptsOnlyJson() {
        // given
        String apiToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        ResponseEntity<String> response = executePost(CREATE_QR_CODE_ENDPOINT, new CreateQrDto(""),
                acceptHeadersWith(apiToken, MediaType.APPLICATION_JSON_VALUE), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("{\"text\":\"Text is required\"}");
    }

    @Test
    void shouldGet406ForValidTextWhenTheClientAcceptsOnlyJson() {
        // given
        String apiToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        ResponseEntity<String> response = executePost(CREATE_QR_CODE_ENDPOINT, new CreateQrDto("test"),
                acceptHeadersWith(apiToken, MediaType.APPLICATION_JSON_VALUE), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_ACCEPTABLE);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).isEqualTo("{\"message\":\"The requested response media type is not available\"}");
    }

    @Test
    void shouldReturnPngForAWildcardAccept() {
        // given
        String apiToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        ResponseEntity<byte[]> response = executePost(CREATE_QR_CODE_ENDPOINT, new CreateQrDto("test"),
                acceptHeadersWith(apiToken, "application/json, */*;q=0.5"), byte[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
    }

    private HttpHeaders acceptHeadersWith(String apiToken, String accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, accept);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
        return headers;
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
