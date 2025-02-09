package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.model.ProductEntity;
import com.awesome.testing.model.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static com.awesome.testing.factory.ProductFactory.getRandomProduct;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetSingleProductControllerTest extends AbstractProductTest {

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGetProductById() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);

        // when
        ResponseEntity<ProductEntity> response = executeGet(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getHeadersWith(clientToken),
                ProductEntity.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getName()).isEqualTo(testProduct.getName());
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet401AsUnauthorized() {
        // given
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);

        // when
        ResponseEntity<ProductEntity> response = executeGet(
                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                getJsonOnlyHeaders(),
                ProductEntity.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void shouldGet404ForWrongProduct() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct = getRandomProduct();
        productRepository.save(testProduct);

        // when
        ResponseEntity<ProductEntity> response = executeGet(
                PRODUCTS_ENDPOINT + "/6666",
                getHeadersWith(clientToken),
                ProductEntity.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

}