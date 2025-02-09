package com.awesome.testing.service;

import com.awesome.testing.dto.user.UserEditDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.exception.CustomException;
import com.awesome.testing.exception.UserNotFoundException;
import com.awesome.testing.security.AuthenticationHandler;
import jakarta.servlet.http.HttpServletRequest;
import com.awesome.testing.model.User;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.awesome.testing.utils.EntityUpdater.updateIfNotNull;

@Service
@Transactional
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationHandler authenticationHandler;

    public String signIn(String username, String password) {
        authenticationHandler.authUser(username, password);
        User user = getUser(username);
        return jwtTokenProvider.createToken(username, user.getRoles());
    }

    @Transactional
    public void signup(UserRegisterDto userRegisterDto) {
        userRepository.findByUsername(userRegisterDto.getUsername())
                .ifPresentOrElse(
                        it -> returnBadRequest(),
                        () -> userRepository.save(getUser(userRegisterDto))
                );
    }

    public void delete(String username) {
        getUser(username);
        userRepository.deleteByUsername(username);
    }

    public User search(String username) {
        return getUser(username);
    }

    public User whoAmI(HttpServletRequest req) {
        String username = jwtTokenProvider.getUsername(jwtTokenProvider.extractTokenFromRequest(req));
        return getUser(username);
    }

    public String refresh(String username) {
        User user = getUser(username);
        return jwtTokenProvider.createToken(username, user.getRoles());
    }

    public List<User> getAll() {
        return userRepository.findAll();
    }

    public User edit(String username, UserEditDto userDto) {
        User existingUser = getUser(username);

        updateIfNotNull(userDto.getEmail(), User::setEmail, existingUser);
        updateIfNotNull(userDto.getFirstName(), User::setFirstName, existingUser);
        updateIfNotNull(userDto.getLastName(), User::setLastName, existingUser);
        updateIfNotNull(userDto.getRoles(), User::setRoles, existingUser);

        return userRepository.save(existingUser);
    }

    @SuppressWarnings("unused")
    public boolean exists(String username) {
        getUser(username);
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

    private void returnBadRequest() {
        throw new CustomException("Username is already in use", HttpStatus.BAD_REQUEST);
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("The user doesn't exist"));
    }

}
