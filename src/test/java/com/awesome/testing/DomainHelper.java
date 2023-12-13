package com.awesome.testing;

import com.awesome.testing.dto.LoginDTO;
import com.awesome.testing.dto.LoginResponseDTO;
import com.awesome.testing.dto.UserRegisterDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.text.MessageFormat;

public abstract class DomainHelper extends HttpHelper {

    protected static final String LOGIN_ENDPOINT = "/users/signin";
    protected static final String REGISTER_ENDPOINT = "/users/signup";
    protected static final String USERS_ENDPOINT = "/users";

    protected static final String MISSING_USER = "The user doesn't exist";

    protected <T> ResponseEntity<T> attemptLogin(LoginDTO loginDetails, Class<T> clazz) {
        return executePost(
                LOGIN_ENDPOINT,
                loginDetails,
                getJsonOnlyHeaders(),
                clazz);
    }

    @SuppressWarnings("ConstantConditions")
    protected String registerAndThenLoginSavingToken(UserRegisterDTO userRegisterDTO) {
        executePost(
                REGISTER_ENDPOINT,
                userRegisterDTO,
                getJsonOnlyHeaders(),
                Void.class);

        String cookie = executePost(
                LOGIN_ENDPOINT,
                new LoginDTO(userRegisterDTO.getUsername(), userRegisterDTO.getPassword()),
                getJsonOnlyHeaders(),
                LoginResponseDTO.class)
                .getHeaders()
                .get("Set-Cookie")
                .get(0);

        return getTokenValueFromCookie(cookie);
    }

    protected String getTokenValueFromCookie(String cookie) {
        String[] parts = cookie.split(";")[0].split("=");
        return parts[1];
    }

    protected String getUserEndpoint(String username) {
        return MessageFormat.format("/users/{0}", username);
    }

    public HttpHeaders getHeadersWith(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add(HttpHeaders.COOKIE, "token=" + token);
        return headers;
    }

}
