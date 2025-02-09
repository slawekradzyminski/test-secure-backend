package com.awesome.testing.service;

import com.awesome.testing.dto.UserEditDto;
import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.exception.CustomException;
import com.awesome.testing.exception.UserNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;
import java.util.Optional;

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
    public void signup(UserRegisterDto userRegisterDto) {
        User user = userRepository.findByUsername(userRegisterDto.getUsername());
        Optional.ofNullable(user)
                .ifPresentOrElse(
                        it -> returnBadRequest(),
                        () -> userRepository.save(getUser(userRegisterDto))
                );
    }

    private void returnBadRequest() {
        throw new CustomException("Username is already in use", HttpStatus.BAD_REQUEST);
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

    public User whoAmI(HttpServletRequest req) {
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

    public User edit(String username, UserEditDto userDto) {
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

        return userRepository.save(existingUser);
    }

    @SuppressWarnings("unused")
    public boolean exists(String username) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new UserNotFoundException("User not found");
        }
        return true;
    }

    private User getUser(UserRegisterDto userRegisterDTO) {
        User user = new User();
        user.setUsername(userRegisterDTO.getUsername());
        user.setFirstName(userRegisterDTO.getFirstName());
        user.setLastName(userRegisterDTO.getLastName());
        user.setRoles(userRegisterDTO.getRoles());
        user.setEmail(userRegisterDTO.getEmail());
        user.setPassword(passwordEncoder.encode(userRegisterDTO.getPassword()));
        return user;
    }

}
