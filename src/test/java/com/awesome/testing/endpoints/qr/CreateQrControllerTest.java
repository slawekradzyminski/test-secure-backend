package com.awesome.testing.endpoints.qr;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.qr.CreateQrDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.qr.QrGenerator;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class CreateQrControllerTest extends DomainHelper {

    private final String CREATE_QR_CODE_ENDPOINT = "/qr/create";

    @TempDir
    private File tempDir;

    @SuppressWarnings("ConstantConditions")
    @SneakyThrows
    @Test
    public void shouldGenerateQrCodeAsAdmin() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String apiToken = registerAndThenLoginSavingToken(user);
        String randomText = getRandomText();
        CreateQrDto createQrDto = new CreateQrDto(randomText);

        // when
        ResponseEntity<byte[]> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeadersWith(apiToken), byte[].class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        File qrCodeFile = new File(tempDir, "testQrCode.png");
        try (FileOutputStream fos = new FileOutputStream(qrCodeFile)) {
            fos.write(response.getBody());
        }
        assertThat(QrGenerator.readQRCode(qrCodeFile)).isEqualTo(randomText);
    }

    @Test
    public void shouldGet403AsUnauthorized() {
        // given
        CreateQrDto createQrDto = new CreateQrDto(getRandomText());

        // when
        ResponseEntity<?> response = executePost(CREATE_QR_CODE_ENDPOINT, createQrDto,
                getImageHeaders(), Void.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private String getRandomText() {
        return RandomStringUtils.randomAlphanumeric(30);
    }

}
