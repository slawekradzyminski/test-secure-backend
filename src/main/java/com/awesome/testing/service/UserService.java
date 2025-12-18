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

    static final String DEFAULT_CHAT_SYSTEM_PROMPT = """
            You are an engineering copilot for our internal product team.
            - Give concise, accurate answers grounded in provided context.
            - Ask clarifying questions when requirements are underspecified.
            - Highlight security or privacy concerns as soon as they matter.
            - When tooling or APIs are relevant, describe how you would use them and cite the component.
            """;

    static final String DEFAULT_TOOL_SYSTEM_PROMPT = """
            You are a tool-calling shopping assistant for our training store.
            Tools available:
            - list_products: returns ONLY id and name for a catalog slice. Accepts offset, limit, category (e.g., "electronics"), and inStockOnly.
            - get_product_snapshot: fetch one product by name or productId (id, name, description, price, stockQuantity, category, imageUrl).

            Tool rules:
            - Never answer from memory; ground every product fact in a tool response.
            - For broad questions, call list_products first, then snapshot every product you mention.
            - For specific SKUs, call get_product_snapshot before replying.
            - For comparisons, retrieve list_products followed by snapshots for each SKU mentioned.
            - If a product is missing, be transparent and ask for another name/id; never fabricate details.
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

    public void logout(String username) {
        refreshTokenService.removeAllTokensForUser(username);
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

    public String getChatSystemPrompt(String username) {
        String prompt = getUser(username).getChatSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            return DEFAULT_CHAT_SYSTEM_PROMPT.strip();
        }
        return prompt;
    }

    public UserEntity updateChatSystemPrompt(String username, String newPrompt) {
        UserEntity user = getUser(username);
        user.setChatSystemPrompt(newPrompt);
        return userRepository.save(user);
    }

    public String getToolSystemPrompt(String username) {
        String prompt = getUser(username).getToolSystemPrompt();
        if (prompt == null || prompt.isBlank()) {
            return DEFAULT_TOOL_SYSTEM_PROMPT.strip();
        }
        return prompt;
    }

    public UserEntity updateToolSystemPrompt(String username, String newPrompt) {
        UserEntity user = getUser(username);
        user.setToolSystemPrompt(newPrompt);
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
        return UserEntity.builder()
                .username(userRegisterDto.getUsername())
                .firstName(userRegisterDto.getFirstName())
                .lastName(userRegisterDto.getLastName())
                .roles(userRegisterDto.getRoles())
                .email(userRegisterDto.getEmail())
                .password(passwordEncoder.encode(userRegisterDto.getPassword()))
                .chatSystemPrompt(DEFAULT_CHAT_SYSTEM_PROMPT.strip())
                .toolSystemPrompt(DEFAULT_TOOL_SYSTEM_PROMPT.strip())
                .build();
    }

    private UserEntity getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException("The user doesn't exist"));
    }

}
