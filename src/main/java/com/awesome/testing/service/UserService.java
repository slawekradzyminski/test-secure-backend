package com.awesome.testing.service;

import com.awesome.testing.dto.UserEditDTO;
import com.awesome.testing.exception.CustomException;
import com.awesome.testing.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import com.awesome.testing.model.Role;
import com.awesome.testing.model.User;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Collections;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;

    public String signIn(String username, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
            return jwtTokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
        } catch (BadCredentialsException e) {
            throw new CustomException("Invalid username/password supplied", HttpStatus.UNPROCESSABLE_ENTITY);
        } catch (AuthenticationException e) {
            throw new CustomException("Unauthorized", HttpStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    public void signup(User user) {
        if (!userRepository.existsByUsername(user.getUsername())) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            if (user.getRoles() == null || user.getRoles().isEmpty()) {
                user.setRoles(Collections.singletonList(Role.ROLE_CLIENT));
            }
            userRepository.save(user);
        } else {
            throw new CustomException("Username is already in use", HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    public void delete(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("The user doesn't exist");
        }
        userRepository.deleteByUsername(username);
    }

    public User search(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("The user doesn't exist");
        }
        return user;
    }

    public User whoami(HttpServletRequest req) {
        String username = jwtTokenProvider.getUsername(jwtTokenProvider.extractTokenFromRequest(req));
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("The user doesn't exist");
        }
        return user;
    }

    public String refresh(String username) {
        return jwtTokenProvider.createToken(username, userRepository.findByUsername(username).getRoles());
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User edit(String username, UserEditDTO userDto) {
        User existingUser = userRepository.findByUsername(username);
        if (existingUser == null) {
            throw new UserNotFoundException("The user doesn't exist");
        }
        
        if (userDto.getEmail() != null) {
            existingUser.setEmail(userDto.getEmail());
        }
        if (userDto.getFirstName() != null) {
            existingUser.setFirstName(userDto.getFirstName());
        }
        if (userDto.getLastName() != null) {
            existingUser.setLastName(userDto.getLastName());
        }
        if (userDto.getRoles() != null) {
            existingUser.setRoles(userDto.getRoles());
        }
        if (userDto.getPassword() != null) {
            existingUser.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        
        return userRepository.save(existingUser);
    }

    public boolean exists(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }
        return true;
    }

}
