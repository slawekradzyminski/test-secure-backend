package com.awesome.testing.endpoints.users;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.email.EmailDeliveryStatus;
import com.awesome.testing.dto.email.EmailTemplate;
import com.awesome.testing.dto.order.OrderStatus;
import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.AddressEntity;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.EmailEventEntity;
import com.awesome.testing.entity.OrderEntity;
import com.awesome.testing.entity.OrderItemEntity;
import com.awesome.testing.entity.PasswordResetTokenEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.EmailEventRepository;
import com.awesome.testing.repository.PasswordResetTokenRepository;
import com.awesome.testing.repository.RefreshTokenRepository;
import com.awesome.testing.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.List;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class RightToBeForgottenControllerTest extends AbstractEcommerceTest {

    private static final String RIGHT_TO_BE_FORGOTTEN_SUFFIX = "/right-to-be-forgotten";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailEventRepository emailEventRepository;

    @Test
    void shouldDeleteOwnAccountAndOwnedData() {
        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String token = getToken(user);
        seedOwnedData(user.getUsername());

        ResponseEntity<String> response = executeDelete(
                getRightToBeForgottenEndpoint(user.getUsername()),
                getHeadersWith(token),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertAllUserDataDeleted(user.getUsername());

        ResponseEntity<ErrorDto> loginAfterDeletion = attemptLogin(
                new LoginDto(user.getUsername(), user.getPassword()),
                ErrorDto.class
        );
        assertThat(loginAfterDeletion.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void shouldAllowAdminToDeleteAnotherUsersData() {
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);

        UserRegisterDto user = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerAndLogin(user);
        seedOwnedData(user.getUsername());

        ResponseEntity<String> response = executeDelete(
                getRightToBeForgottenEndpoint(user.getUsername()),
                getHeadersWith(adminToken),
                String.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertAllUserDataDeleted(user.getUsername());
    }

    @Test
    void shouldRejectDeletingAnotherUsersDataAsClient() {
        UserRegisterDto targetUser = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        registerUser(targetUser);

        UserRegisterDto actingUser = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String actingToken = getToken(actingUser);

        ResponseEntity<ErrorDto> response = executeDelete(
                getRightToBeForgottenEndpoint(targetUser.getUsername()),
                getHeadersWith(actingToken),
                ErrorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldReturn401WhenUnauthorized() {
        ResponseEntity<ErrorDto> response = executeDelete(
                getRightToBeForgottenEndpoint("nonexisting"),
                getJsonOnlyHeaders(),
                ErrorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getMessage()).isEqualTo("Unauthorized");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldReturn404WhenUserDoesNotExist() {
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);

        ResponseEntity<ErrorDto> response = executeDelete(
                getRightToBeForgottenEndpoint("nonexisting"),
                getHeadersWith(adminToken),
                ErrorDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().getMessage()).isEqualTo(MISSING_USER);
    }

    private void seedOwnedData(String username) {
        UserEntity user = userRepository.findByUsername(username).orElseThrow();
        ProductEntity product = setupProduct();

        cartItemRepository.save(CartItemEntity.builder()
                .username(username)
                .product(product)
                .quantity(2)
                .price(product.getPrice())
                .build());

        OrderEntity order = OrderEntity.builder()
                .username(username)
                .totalAmount(product.getPrice())
                .status(OrderStatus.PENDING)
                .shippingAddress(AddressEntity.builder()
                        .street("Test Street 1")
                        .city("Test City")
                        .state("TS")
                        .zipCode("00-001")
                        .country("PL")
                        .build())
                .build();
        order.addItem(OrderItemEntity.builder()
                .product(product)
                .quantity(1)
                .price(product.getPrice())
                .build());
        orderRepository.save(order);

        passwordResetTokenRepository.save(PasswordResetTokenEntity.builder()
                .tokenHash("hash-" + username)
                .requestedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .requestIp("127.0.0.1")
                .userAgent("JUnit")
                .user(user)
                .build());

        emailEventRepository.save(EmailEventEntity.builder()
                .user(user)
                .type(EmailTemplate.GENERIC)
                .status(EmailDeliveryStatus.QUEUED)
                .recipientEmail(user.getEmail())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private void assertAllUserDataDeleted(String username) {
        assertThat(userRepository.findByUsername(username)).isEmpty();
        assertThat(cartItemRepository.countByUsername(username)).isZero();
        assertThat(orderRepository.countByUsername(username)).isZero();
        assertThat(refreshTokenRepository.countByUserUsername(username)).isZero();
        assertThat(passwordResetTokenRepository.countByUserUsername(username)).isZero();
        assertThat(emailEventRepository.countByUserUsername(username)).isZero();
    }

    private String getRightToBeForgottenEndpoint(String username) {
        return getUserEndpoint(username) + RIGHT_TO_BE_FORGOTTEN_SUFFIX;
    }
}
