package com.awesome.testing;

import com.awesome.testing.dto.UserRegisterDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.text.MessageFormat;

public abstract class DomainHelper extends HttpHelper {

    protected static final String LOGIN_ENDPOINT = "/users/signin";
    protected static final String REGISTER_ENDPOINT = "/users/signup";

    protected static final String MISSING_USER = "The user doesn't exist";

    protected ResponseEntity<String> registerUser(UserRegisterDTO userRegisterDTO) {
        return executePost(
                REGISTER_ENDPOINT,
                userRegisterDTO,
                String.class);
    }

    protected String getUserEndpoint(String username) {
        return MessageFormat.format("/users/{0}", username);
    }

    protected HttpHeaders getHeadersWith(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, MessageFormat.format("Bearer {0}", token));
        return headers;
    }

}
