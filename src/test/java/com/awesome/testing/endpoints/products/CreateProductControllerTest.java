package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.ProductFactory.getRandomProductCreate;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class CreateProductControllerTest extends AbstractEcommerceTest {

    @Test
    void shouldCreateProductAsAdmin() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        ProductCreateDto productCreateDto = getRandomProductCreate();

        // when
        ResponseEntity<ProductDto> response = executePost(
                PRODUCTS_ENDPOINT,
                productCreateDto,
                getHeadersWith(adminToken),
                ProductDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(productCreateDto.getName());
        assertThat(response.getBody().getId()).isNotNull();
        assertThat(response.getBody().getCreatedAt()).isNotNull();
        assertThat(response.getBody().getUpdatedAt()).isNotNull();
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldGet400ForInvalidBody() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        ProductCreateDto productCreateDto = getRandomProductCreate();
        productCreateDto.setPrice(BigDecimal.valueOf(10.345));

        // when
        ResponseEntity<Map<String, String>> response = executePost(
                PRODUCTS_ENDPOINT,
                productCreateDto,
                getHeadersWith(adminToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("price")).isEqualTo("Price must have at most 8 digits and 2 decimals");
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void shouldRequireDescriptionWhenCreatingProduct() {
        // given
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        ProductCreateDto productCreateDto = getRandomProductCreate();
        productCreateDto.setDescription("");

        // when
        ResponseEntity<Map<String, String>> response = executePost(
                PRODUCTS_ENDPOINT,
                productCreateDto,
                getHeadersWith(adminToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("description")).isEqualTo("Product description is required");
    }

    @Test
    void shouldGet401AsUnauthorized() {
        ProductCreateDto productCreateDto = getRandomProductCreate();

        // when
        ResponseEntity<Object> response = executePost(
                PRODUCTS_ENDPOINT,
                productCreateDto,
                getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void shouldGet403AsClient() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductCreateDto productCreateDto = getRandomProductCreate();

        // when
        ResponseEntity<ProductEntity> response = executePost(
                PRODUCTS_ENDPOINT,
                productCreateDto,
                getHeadersWith(clientToken),
                ProductEntity.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

}
