package com.awesome.testing.endpoints.inventory;

import com.awesome.testing.dto.inventory.InventoryAdjustmentDto;
import com.awesome.testing.dto.inventory.InventoryItemDto;
import com.awesome.testing.dto.inventory.InventoryMovementDto;
import com.awesome.testing.dto.order.PageDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

class AdminInventoryControllerTest extends AbstractEcommerceTest {

    private static final String INVENTORY_ENDPOINT = "/api/v1/admin/inventory";

    @Test
    void adminCanListAdjustAndReadMovementHistoryUsingTheirAuthenticatedUsername() {
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String token = getToken(admin);
        ProductEntity product = productRepository.save(ProductEntity.builder()
                .name("Inventory test product")
                .description("Used by the inventory endpoint test")
                .price(BigDecimal.TEN)
                .stockQuantity(1)
                .category("Tests")
                .build());

        ResponseEntity<PageDto<InventoryItemDto>> listing = executeGet(
                INVENTORY_ENDPOINT,
                getHeadersWith(token),
                new ParameterizedTypeReference<>() {});
        ResponseEntity<InventoryMovementDto> adjustment = executePost(
                INVENTORY_ENDPOINT + "/" + product.getId() + "/adjustments",
                InventoryAdjustmentDto.builder()
                        .delta(2)
                        .reason("delivery")
                        .requestId(UUID.randomUUID())
                        .build(),
                getHeadersWith(token),
                InventoryMovementDto.class);
        ResponseEntity<PageDto<InventoryMovementDto>> history = executeGet(
                INVENTORY_ENDPOINT + "/" + product.getId() + "/movements",
                getHeadersWith(token),
                new ParameterizedTypeReference<>() {});

        assertThat(listing.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listing.getBody().getContent()).extracting(InventoryItemDto::getProductId)
                .contains(product.getId());
        assertThat(adjustment.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(adjustment.getBody().getActor()).isEqualTo(admin.getUsername());
        assertThat(adjustment.getBody().getQuantityAfter()).isEqualTo(3);
        assertThat(history.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(history.getBody().getContent()).singleElement()
                .extracting(InventoryMovementDto::getActor)
                .isEqualTo(admin.getUsername());
    }

    @Test
    void inventoryEndpointsRequireAnAdministrator() {
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);

        ResponseEntity<Object> anonymous = executeGet(
                INVENTORY_ENDPOINT,
                getJsonOnlyHeaders(),
                Object.class);
        ResponseEntity<Object> forbidden = executeGet(
                INVENTORY_ENDPOINT,
                getHeadersWith(clientToken),
                Object.class);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
