package com.awesome.testing;

import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.text.MessageFormat;
import java.util.List;

public abstract class DomainHelper extends HttpHelper {

    protected static final String LOGIN_ENDPOINT = "/api/v1/users/signin";
    protected static final String REGISTER_ENDPOINT = "/api/v1/users/signup";
    protected static final String USERS_ENDPOINT = "/api/v1/users";

    protected static final String MISSING_USER = "The user doesn't exist";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    protected <T> ResponseEntity<T> attemptLogin(LoginDto loginDetails, Class<T> clazz) {
        return executePost(
                LOGIN_ENDPOINT,
                loginDetails,
                getJsonOnlyHeaders(),
                clazz);
    }

    protected void registerUser(UserRegisterDto userRegisterDto) {
        registerAndLogin(userRegisterDto);
    }

    protected String getToken(UserRegisterDto userRegisterDto) {
        return registerAndLogin(userRegisterDto).getToken();
    }

    protected LoginResponseDto registerAndLogin(UserRegisterDto userRegisterDto) {
        if (userRegisterDto.getRoles() != null && userRegisterDto.getRoles().contains(Role.ROLE_ADMIN)) {
            createUserDirectly(userRegisterDto);
        } else {
            ResponseEntity<String> signupResponse = executePost(
                    REGISTER_ENDPOINT,
                    userRegisterDto,
                    getJsonOnlyHeaders(),
                    String.class
            );
            if (!signupResponse.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Signup failed with status " + signupResponse.getStatusCode()
                        + " body=" + signupResponse.getBody());
            }
        }

        LoginDto loginDto = LoginDto.builder()
                .username(userRegisterDto.getUsername())
                .password(userRegisterDto.getPassword())
                .build();

        ResponseEntity<LoginResponseDto> loginResponseEntity = executePost(
                LOGIN_ENDPOINT,
                loginDto,
                getJsonOnlyHeaders(),
                LoginResponseDto.class
        );

        if (!loginResponseEntity.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Login failed with status " + loginResponseEntity.getStatusCode()
                    + " body=" + loginResponseEntity.getBody());
        }

        LoginResponseDto loginResponse = loginResponseEntity.getBody();
        if (loginResponse == null || loginResponse.getToken() == null) {
            throw new IllegalStateException("Login failed, token not found");
        }
        return loginResponse;
    }

    private void createUserDirectly(UserRegisterDto userRegisterDto) {
        UserEntity user = UserEntity.builder()
                .username(userRegisterDto.getUsername())
                .email(userRegisterDto.getEmail())
                .password(passwordEncoder.encode(userRegisterDto.getPassword()))
                .roles(List.copyOf(userRegisterDto.getRoles()))
                .firstName(userRegisterDto.getFirstName())
                .lastName(userRegisterDto.getLastName())
                .build();
        userRepository.save(user);
    }

    protected String getUserEndpoint(String username) {
        return MessageFormat.format("/api/v1/users/{0}", username);
    }

    protected HttpHeaders getHeadersWith(String token) {
        HttpHeaders headers = getJsonOnlyHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, MessageFormat.format("Bearer {0}", token));
        return headers;
    }

}
