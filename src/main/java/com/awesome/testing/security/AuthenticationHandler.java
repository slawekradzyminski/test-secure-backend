package com.awesome.testing.security;

import com.awesome.testing.dto.users.LoginDto;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.exception.ApiException;

import com.awesome.testing.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticationHandler<T extends UserEntity> {

    private final JwtTokenUtil<T> jwtTokenUtil;
    private final AuthenticationManager authenticationManager;
    private final IUserRepository<T> userRepository;

    public String authenticateUserAndGetToken(LoginDto loginDto) {
        String username = loginDto.getUsername();
        String password = loginDto.getPassword();
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            return jwtTokenUtil.createToken(username, userRepository.findByUsername(username).getRoles());
        } catch (AuthenticationException e) {
            throw new ApiException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

}
