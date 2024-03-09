package com.awesome.testing.endpoints.users;

import static com.awesome.testing.testutil.UserUtil.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;

public class ProfilePictureControllerTest extends DomainHelper {

    @TempDir
    private File tempDir;

    @SuppressWarnings("ConstantConditions")
    @SneakyThrows
    @Test
    public void shouldUploadAndRetrieveProfilePictureAsAuthorizedUser() {
        // given
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String apiToken = registerAndThenLoginSavingToken(user);
        MultipartFile profilePicture = getTestImage();
        String endpointUrl = "/users/" + user.getUsername() + "/profile-picture";

        // when
        ResponseEntity<?> uploadResponse = sendPicture(endpointUrl, profilePicture, getMultipartHeadersWith(apiToken));

        // then
        assertThat(uploadResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        // when
        ResponseEntity<byte[]> retrieveResponse = executeGet(endpointUrl, getMultipartHeadersWith(apiToken), byte[].class);

        // then
        assertThat(retrieveResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        File retrievedProfilePicture = new File(tempDir, "retrievedProfilePicture.png");
        try (FileOutputStream fos = new FileOutputStream(retrievedProfilePicture)) {
            fos.write(retrieveResponse.getBody());
        }
        byte[] originalFileBytes = getBytesFromImage(profilePicture);
        byte[] retrievedFileBytes = Files.readAllBytes(retrievedProfilePicture.toPath());
        assertThat(retrievedFileBytes).isEqualTo(originalFileBytes);
    }

    @SneakyThrows
    private MultipartFile getTestImage() {
        Path path = Paths.get("src/test/resources/avatar.png");
        byte[] content = Files.readAllBytes(path);
        return new MockMultipartFile("file", "avatar.png", "image/png", content);
    }

    @SneakyThrows
    private byte[] getBytesFromImage(MultipartFile profilePicture) {
        return profilePicture.getBytes();
    }

    private ResponseEntity<?> sendPicture(String url, MultipartFile file, HttpHeaders headers) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(convertMultipartFileToFile(file)));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class);
    }

    @SneakyThrows
    @SuppressWarnings("all")
    private File convertMultipartFileToFile(MultipartFile file) {
        File convFile = new File(tempDir, file.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(file.getBytes());
        }
        return convFile;
    }

    private HttpHeaders getMultipartHeadersWith(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.MULTIPART_FORM_DATA_VALUE);
        headers.add(HttpHeaders.COOKIE, "token=" + token);
        return headers;
    }

}
