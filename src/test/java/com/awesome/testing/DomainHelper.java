package com.awesome.testing;

import com.awesome.testing.dto.LoginDto;
import com.awesome.testing.dto.LoginResponseDto;
import com.awesome.testing.dto.UserRegisterDto;
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

    protected void registerUser(UserRegisterDto userRegisterDto) {
        getToken(userRegisterDto);
    }

    @SuppressWarnings("ConstantConditions")
    protected String getToken(UserRegisterDto userRegisterDto) {
        executePost(
                REGISTER_ENDPOINT,
                userRegisterDto,
                getJsonOnlyHeaders(),
                String.class
        );

        LoginDto loginDTO = LoginDto.builder()
                .username(userRegisterDto.getUsername())
                .password(userRegisterDto.getPassword())
                .build();

        LoginResponseDto loginResponse = executePost(
                LOGIN_ENDPOINT,
                loginDTO,
                getJsonOnlyHeaders(),
                LoginResponseDto.class
        ).getBody();

        if (loginResponse != null) {
            return loginResponse.getToken();
        }

        throw new IllegalStateException("Login failed, token not found");
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
