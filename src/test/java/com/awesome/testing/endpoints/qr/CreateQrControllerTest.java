package com.awesome.testing.endpoints.qr;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.qr.CreateQrDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
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
    void shouldGenerateQrCodeAsAdmin() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = getToken(admin);
        String randomText = getRandomText();
        CreateQrDto createQrDto = new CreateQrDto(randomText);

        // when
        ResponseEntity<byte[]> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeadersWith(apiToken), byte[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(response.getBody()).isNotEmpty();
        var image = ImageIO.read(new ByteArrayInputStream(response.getBody()));
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        assertThat(new MultiFormatReader().decode(bitmap).getText()).isEqualTo(randomText);
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
        CreateQrDto createQrDto = new CreateQrDto(getRandomText());

        // when
        ResponseEntity<?> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeaders(), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private String getRandomText() {
        return RandomStringUtils.secure().nextAlphanumeric(30);
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
