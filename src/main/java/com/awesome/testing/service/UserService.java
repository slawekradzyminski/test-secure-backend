package com.awesome.testing.service;

import jakarta.servlet.http.HttpServletRequest;

import com.awesome.testing.dto.LoginDTO;
import com.awesome.testing.dto.UserRegisterResponseDTO;
import com.awesome.testing.exception.CustomException;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.awesome.testing.model.User;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public String signIn(LoginDTO loginDetails) {
        String username = loginDetails.getUsername();
        String password = loginDetails.getPassword();
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            return jwtTokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
        } catch (AuthenticationException e) {
            throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public void save(User user) {
        userRepository.save(user);
    }

    public UserRegisterResponseDTO signUp(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new CustomException("Username is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        String token = jwtTokenProvider.createToken(user.getUsername(), user.getRoles());
        return UserRegisterResponseDTO.builder().token(token).build();
    }

    public void delete(String username) {
        search(username);
        userRepository.deleteByUsername(username);
    }

    public User search(String username) {
        return Optional.ofNullable(userRepository.findByUsername(username))
                .orElseThrow(() -> new CustomException("The user doesn't exist", HttpStatus.NOT_FOUND));
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User whoAmI(HttpServletRequest req) {
        String token = jwtTokenProvider.extractTokenFromRequest(req);
        return userRepository.findByUsername(jwtTokenProvider.getUsername(token));
    }

    public String refresh(String username) {
        return jwtTokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
    }

}
