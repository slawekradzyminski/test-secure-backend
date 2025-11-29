package com.awesome.testing.service;

import com.awesome.testing.dto.user.TokenPair;
import com.awesome.testing.dto.user.UserEditDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.controller.exception.UserNotFoundException;
import com.awesome.testing.security.AuthenticationHandler;
import jakarta.servlet.http.HttpServletRequest;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.entity.RefreshTokenEntity;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.JwtTokenProvider;
import com.awesome.testing.service.token.RefreshTokenService;
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
    private final RefreshTokenService refreshTokenService;

    public TokenPair signIn(String username, String password) {
        authenticationHandler.authUser(username, password);
        UserEntity user = getUser(username);
        String jwt = jwtTokenProvider.createToken(username, user.getRoles());
        String refreshToken = refreshTokenService.createToken(user).getToken();
        return TokenPair.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .build();
    }

    @Transactional
    public void signup(UserRegisterDto userRegisterDto) {
        userRepository.findByUsernameOrEmail(userRegisterDto.getUsername(), userRegisterDto.getEmail())
                .ifPresent(existingUser -> handleDuplicateUsersErrors(userRegisterDto, existingUser));

        userRepository.save(getUser(userRegisterDto));
    }

    public void delete(String username) {
        getUser(username);
        refreshTokenService.removeAllTokensForUser(username);
        userRepository.deleteByUsername(username);
    }

    public UserEntity search(String username) {
        return getUser(username);
    }

    public UserEntity whoAmI(HttpServletRequest req) {
        String username = jwtTokenProvider.getUsername(jwtTokenProvider.extractTokenFromRequest(req));
        return getUser(username);
    }

    public TokenPair refresh(String refreshToken) {
        RefreshTokenEntity rotatedToken = refreshTokenService.rotateToken(refreshToken);
        UserEntity user = rotatedToken.getUser();
        String jwt = jwtTokenProvider.createToken(user.getUsername(), user.getRoles());
        String rotatedRefreshToken = rotatedToken.getToken();
        return TokenPair.builder()
                .token(jwt)
                .refreshToken(rotatedRefreshToken)
                .build();
    }

    public void logout(String refreshToken, String username) {
        refreshTokenService.revokeToken(refreshToken, username);
    }

    public List<UserEntity> getAll() {
        return userRepository.findAll();
    }

    public UserEntity edit(String username, UserEditDto userDto) {
        UserEntity existingUser = getUser(username);

        updateIfNotNull(userDto.getEmail(), UserEntity::setEmail, existingUser);
        updateIfNotNull(userDto.getFirstName(), UserEntity::setFirstName, existingUser);
        updateIfNotNull(userDto.getLastName(), UserEntity::setLastName, existingUser);

        return userRepository.save(existingUser);
    }

    public String getSystemPrompt(String username) {
        return getUser(username).getSystemPrompt();
    }

    public UserEntity updateSystemPrompt(String username, String newPrompt) {
        UserEntity user = getUser(username);
        user.setSystemPrompt(newPrompt);
        return userRepository.save(user);
    }

    @SuppressWarnings("unused")
    public boolean exists(String username) {
        getUser(username);
        return true;
    }

    private static void handleDuplicateUsersErrors(UserRegisterDto userRegisterDto, UserEntity existingUser) {
        if (existingUser.getUsername().equals(userRegisterDto.getUsername())) {
            throw new CustomException("Username is already in use", HttpStatus.BAD_REQUEST);
        }
        throw new CustomException("Email is already in use", HttpStatus.BAD_REQUEST);
    }

    private UserEntity getUser(UserRegisterDto userRegisterDto) {
        UserEntity user = new UserEntity();
        user.setUsername(userRegisterDto.getUsername());
        user.setFirstName(userRegisterDto.getFirstName());
        user.setLastName(userRegisterDto.getLastName());
        user.setRoles(userRegisterDto.getRoles());
        user.setEmail(userRegisterDto.getEmail());
        user.setPassword(passwordEncoder.encode(userRegisterDto.getPassword()));
        return user;
    }

    private UserEntity getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("The user doesn't exist"));
    }

}
