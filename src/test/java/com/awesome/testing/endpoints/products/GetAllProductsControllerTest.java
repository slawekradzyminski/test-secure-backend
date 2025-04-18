package com.awesome.testing.endpoints.products;

import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static com.awesome.testing.util.TypeReferenceUtil.productListTypeReference;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

public class GetAllProductsControllerTest extends AbstractEcommerceTest {

    @Test
    public void shouldGetAllProductsWhenAuthenticated() {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        String clientToken = getToken(client);
        ProductEntity testProduct1 = setupProduct();
        ProductEntity testProduct2 = setupProduct();

        // when
        ResponseEntity<List<ProductDto>> response = executeGet(
                PRODUCTS_ENDPOINT,
                getHeadersWith(clientToken),
                productListTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).map(ProductDto::getName)
                .containsExactlyInAnyOrder(testProduct1.getName(), testProduct2.getName());
    }

    @Test
    public void shouldGet401AsNotAuthenticated() {
        // when
        ResponseEntity<Map<String, String>> response = executeGet(
                PRODUCTS_ENDPOINT,
                getJsonOnlyHeaders(),
                mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

}