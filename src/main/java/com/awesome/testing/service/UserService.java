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

    static final String DEFAULT_SYSTEM_PROMPT = """
            You are a tool-calling shopping assistant for our training store.
            Tools you can call:
            - get_product_snapshot: fetch one product by name or productId (returns id, name, description, price, stockQuantity, category, imageUrl).
            - list_products: browse the catalog with optional filters (category, maxPrice, minPrice, inStockOnly).

            Tool-calling rules:
            - Never answer from memory; ground every product fact in a tool response.
            - For any question about a specific product, FIRST call get_product_snapshot with the provided name or productId.
            - For comparisons or recommendations across multiple products, call get_product_snapshot for the named product (if any), then use list_products to assemble alternatives.
            - If no product is found, state that clearly and ask for a different name/id instead of fabricating details.
            - Keep responses concise and focused on the returned fields: description, price (with currency), and stockQuantity.
            - Do not expose internal tool schemas; just present the results to the user.
            """;

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
        String systemPrompt = getUser(username).getSystemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return DEFAULT_SYSTEM_PROMPT.strip();
        }
        return systemPrompt;
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
        user.setSystemPrompt(DEFAULT_SYSTEM_PROMPT.strip());
        return user;
    }

    private UserEntity getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("The user doesn't exist"));
    }

}
