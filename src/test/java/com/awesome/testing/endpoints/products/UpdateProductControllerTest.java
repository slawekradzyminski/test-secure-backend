package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.ProductDto;
import com.awesome.testing.dto.ProductUpdateDto;
import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.ProductFactory.*;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

public class UpdateProductControllerTest extends AbstractProductTest {

    @Test
    public void shouldUpdateProductAsAdmin() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        ProductUpdateDto productUpdateDto = getRandomProductUpdate();

        // when
        ResponseEntity<ProductDto> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                productUpdateDto,
                getHeadersWith(adminToken),
                ProductDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(productUpdateDto.getName());
    }

    @Test
    public void shouldPartiallyUpdateProductAsAdmin() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        ProductUpdateDto productUpdateDto = ProductUpdateDto.builder()
                .stockQuantity(11)
                .build();

        // when
        ResponseEntity<ProductDto> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                productUpdateDto,
                getHeadersWith(adminToken),
                ProductDto.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getName()).isEqualTo(testProduct.getName());
            assertThat(response.getBody().getStockQuantity()).isEqualTo(productUpdateDto.getStockQuantity());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet400ForInvalidBody() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        ProductUpdateDto productUpdateDto = ProductUpdateDto.builder()
                .stockQuantity(-1)
                .build();

        // when
        ResponseEntity<Map<String, String>> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                productUpdateDto,
                getHeadersWith(adminToken),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("stockQuantity")).isEqualTo("Stock quantity cannot be negative");
    }

    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        ProductUpdateDto productUpdateDto = getRandomProductUpdate();

        // when
        ResponseEntity<Object> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                productUpdateDto,
                getJsonOnlyHeaders(),
                Object.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    public void shouldGet403AsClient() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductUpdateDto productUpdateDto = getRandomProductUpdate();

        // when
        ResponseEntity<ProductEntity> response = executePut(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                productUpdateDto,
                getHeadersWith(clientToken),
                ProductEntity.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    public void shouldGet404ForNotPresentProduct() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);
        UserRegisterDto admin = getRandomUserWithRoles(List.of(Role.ROLE_ADMIN));
        String adminToken = getToken(admin);
        ProductUpdateDto productUpdateDto = getRandomProductUpdate();

        // when
        ResponseEntity<ProductEntity> response = executePut(
                PRODUCTS_ENDPOINT + "/66666",
                productUpdateDto,
                getHeadersWith(adminToken),
                ProductEntity.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

}