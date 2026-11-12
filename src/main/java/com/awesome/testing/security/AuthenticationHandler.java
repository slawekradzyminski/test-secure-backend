package com.awesome.testing.security;

import com.awesome.testing.controller.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class AuthenticationHandler {

    private final AuthenticationManager authenticationManager;

    public void authUser(String username, String password) {
        if (PasswordPolicy.exceedsBcryptLimit(password)) {
            // BCrypt would compare only the first 72 bytes and accept any suffix.
            throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_CONTENT);
        }
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (BadCredentialsException e) {
            throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_CONTENT, e);
        } catch (AuthenticationException e) {
            throw new CustomException("Unauthorized", HttpStatus.UNAUTHORIZED, e);
        }
    }

}
