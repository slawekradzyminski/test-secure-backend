package com.awesome.testing;

import com.awesome.testing.dto.users.LoginDto;
import com.awesome.testing.dto.users.LoginResponseDto;
import com.awesome.testing.dto.users.UserRegisterDto;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.text.MessageFormat;

public abstract class DomainHelper extends HttpHelper {

    protected static final String LOGIN_ENDPOINT = "/users/signin";
    protected static final String REGISTER_ENDPOINT = "/users/signup";
    protected static final String USERS_ENDPOINT = "/users";

    protected static final String MISSING_USER = "The user doesn't exist";

    protected <T> ResponseEntity<T> attemptLogin(LoginDto loginDetails, Class<T> clazz) {
        return executePost(
                LOGIN_ENDPOINT,
                loginDetails,
                getJsonOnlyHeaders(),
                clazz);
    }

    @SuppressWarnings("ConstantConditions")
    protected String registerAndThenLoginSavingToken(UserRegisterDto userRegisterDTO) {
        register(userRegisterDTO);

        String cookie = executePost(
                LOGIN_ENDPOINT,
                new LoginDto(userRegisterDTO.getUsername(), userRegisterDTO.getPassword()),
                getJsonOnlyHeaders(),
                LoginResponseDto.class)
                .getHeaders()
                .get("Set-Cookie")
                .getFirst();

        return getTokenValueFromCookie(cookie);
    }

    public void register(UserRegisterDto userRegisterDTO) {
        executePost(
                REGISTER_ENDPOINT,
                userRegisterDTO,
                getJsonOnlyHeaders(),
                Void.class);
    }

    protected String getTokenValueFromCookie(String cookie) {
        String[] parts = cookie.split(";")[0].split("=");
        return parts[1];
    }

    protected String getUserEndpoint(String username) {
        return MessageFormat.format("/users/{0}", username);
    }

    protected HttpHeaders getHeadersWith(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add(HttpHeaders.COOKIE, "token=" + token);
        return headers;
    }

    protected HttpHeaders getImageHeadersWith(String token) {
        HttpHeaders headers = getImageHeaders();
        headers.add(HttpHeaders.COOKIE, "token=" + token);
        return headers;
    }

}
