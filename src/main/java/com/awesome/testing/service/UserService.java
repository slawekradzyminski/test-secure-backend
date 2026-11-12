package com.awesome.testing.service;

import com.awesome.testing.dto.user.TokenPair;
import com.awesome.testing.dto.user.UserEditDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.controller.exception.UserNotFoundException;
import com.awesome.testing.security.AuthenticationHandler;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.PasswordResetTokenRepository;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.coupon.CartCouponRepository;
import com.awesome.testing.repository.review.ProductReviewRepository;
import com.awesome.testing.repository.wishlist.WishlistItemRepository;
import com.awesome.testing.security.JwtTokenProvider;
import com.awesome.testing.service.token.RefreshTokenService;
import com.awesome.testing.service.token.IssuedRefreshToken;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.service.mfa.MfaChallenge;
import com.awesome.testing.service.mfa.MfaService;
import com.awesome.testing.service.webhook.WebhookService;
import com.awesome.testing.service.tender.TenderDataEraser;
import com.awesome.testing.service.identity.UserIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.awesome.testing.dto.user.Role.ROLE_CLIENT;
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
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailEventRepository emailEventRepository;
    private final CartItemRepository cartItemRepository;
    private final CartCouponRepository cartCouponRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductReviewRepository productReviewRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final EntityManager entityManager;
    private final MfaService mfaService;
    private final WebhookService webhookService;
    private final TenderDataEraser tenderDataEraser;
    private final UserIdentity userIdentity;

    public LoginResponseDto signIn(String username, String password) {
        authenticationHandler.authUser(username, password);
        // The sign-in name may differ in case from the stored username; tokens carry the stored one.
        UserEntity user = userIdentity.findBySignInName(username)
                .orElseThrow(() -> new UserNotFoundException("The user doesn't exist"));
        if (mfaService.isEnabled(user)) {
            MfaChallenge challenge = mfaService.startSignIn(user);
            return LoginResponseDto.mfaChallenge(user, challenge.rawToken(), challenge.expiresAt());
        }
        String jwt = jwtTokenProvider.createToken(user.getUsername(), user.getRoles());
        String refreshToken = refreshTokenService.createToken(user).value();
        TokenPair tokens = TokenPair.builder()
                .token(jwt)
                .refreshToken(refreshToken)
                .build();
        return LoginResponseDto.from(tokens, user);
    }

    @Transactional
    public void signup(UserRegisterDto userRegisterDto) {
        ensureUniqueSignupIdentity(userRegisterDto);

        try {
            userRepository.saveAndFlush(getUser(userRegisterDto));
        } catch (DataIntegrityViolationException ex) {
            handleDuplicateUsersErrors(userRegisterDto, ex);
        }
    }

    public void delete(String username) {
        forget(username);
    }

    public void forget(String username) {
        // Lock order: cart rows, then orders, then the user row - the order checkout and payment results use
        // (they lock the cart or order and then share-lock the user to record an email). Locking the user
        // first and deleting orders later deadlocked with a payment result for the same user.
        cartItemRepository.findByUsernameForUpdate(username);
        // Locks the orders and cancels the open ones first, so their reserved stock goes back to the
        // catalogue (and card payments are refunded) before the order rows are deleted below.
        orderService.cancelOpenOrdersForErasure(username);
        // The cancellations wrote inventory movements and tender ledger rows that reference the managed orders.
        // Write them and detach everything, so deleting the orders below does not trip over those references
        // (the database sets their order_id to null). Row locks are kept until the transaction ends.
        entityManager.flush();
        entityManager.clear();
        // The user row lock keeps a concurrently recorded email event from landing after its rows were
        // deleted and failing the final user delete on email_event_user_id_fkey.
        UserEntity user = userRepository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new UserNotFoundException("The user doesn't exist"));
        refreshTokenService.removeAllTokensForUser(username);
        passwordResetTokenRepository.deleteAllByUser(user);
        emailEventRepository.deleteAllByUser(user);
        cartItemRepository.deleteByUsername(username);
        cartCouponRepository.deleteByUsername(username);
        wishlistItemRepository.deleteByUsername(username);
        productReviewRepository.deleteByUsername(username);
        orderRepository.deleteByUsername(username);
        tenderDataEraser.forget(username);
        mfaService.deleteForUser(user);
        webhookService.deleteForUser(username);
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
        IssuedRefreshToken rotatedToken = refreshTokenService.rotateToken(refreshToken);
        UserEntity user = rotatedToken.user();
        String jwt = jwtTokenProvider.createToken(user.getUsername(), user.getRoles());
        return TokenPair.builder()
                .token(jwt)
                .refreshToken(rotatedToken.value())
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
        String email = UserIdentity.canonicalEmail(userDto.getEmail());
        if (email != null && userIdentity.emailTakenByAnother(email, existingUser.getId())) {
            throw new CustomException("Email is already in use", HttpStatus.BAD_REQUEST);
        }

        updateIfNotNull(email, UserEntity::setEmail, existingUser);
        updateIfNotNull(userDto.getFirstName(), UserEntity::setFirstName, existingUser);
        updateIfNotNull(userDto.getLastName(), UserEntity::setLastName, existingUser);

        return userRepository.saveAndFlush(existingUser);
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

    private void ensureUniqueSignupIdentity(UserRegisterDto userRegisterDto) {
        if (userIdentity.usernameTaken(userRegisterDto.getUsername())) {
            throw new CustomException("Username is already in use", HttpStatus.BAD_REQUEST);
        }
        if (userIdentity.emailTaken(userRegisterDto.getEmail())) {
            throw new CustomException("Email is already in use", HttpStatus.BAD_REQUEST);
        }
    }

    private void handleDuplicateUsersErrors(UserRegisterDto userRegisterDto, DataIntegrityViolationException ex) {
        if (userIdentity.usernameTaken(userRegisterDto.getUsername())) {
            throw new CustomException("Username is already in use", HttpStatus.BAD_REQUEST, ex);
        }
        if (userIdentity.emailTaken(userRegisterDto.getEmail())) {
            throw new CustomException("Email is already in use", HttpStatus.BAD_REQUEST, ex);
        }
        throw ex;
    }

    private UserEntity getUser(UserRegisterDto userRegisterDto) {
        return UserEntity.builder()
                .username(userRegisterDto.getUsername())
                .firstName(userRegisterDto.getFirstName())
                .lastName(userRegisterDto.getLastName())
                .roles(List.of(ROLE_CLIENT))
                .email(UserIdentity.canonicalEmail(userRegisterDto.getEmail()))
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
