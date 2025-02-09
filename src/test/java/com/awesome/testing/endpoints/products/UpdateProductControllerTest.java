//package com.awesome.testing.endpoints.products;
//
//import com.awesome.testing.dto.ProductDto;
//import com.awesome.testing.dto.UserRegisterDto;
//import com.awesome.testing.factory.UserFactory;
//import com.awesome.testing.model.ProductEntity;
//import com.awesome.testing.model.Role;
//import org.junit.jupiter.api.BeforeEach;
//import org.junit.jupiter.api.Test;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.math.BigDecimal;
//import java.util.List;
//import java.util.Map;
//
//import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
//import static org.assertj.core.api.Assertions.assertThat;
//
//public class UpdateProductControllerTest extends AbstractProductTest {
//
//    @Test
//    public void shouldUpdateProductAsAdmin() {
//        // given
//        ProductDto updatedProduct = ProductDto.builder()
//                .name("Updated Product")
//                .description("Updated Description")
//                .price(BigDecimal.valueOf(299.99))
//                .stockQuantity(30)
//                .category("Updated Category")
//                .build();
//
//        // when
//        ResponseEntity<ProductEntity> response = executePut(
//                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
//                updatedProduct,
//                getHeadersWith(adminToken),
//                ProductEntity.class);
//
//        // then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
//        assertThat(response.getBody()).isNotNull();
//        assertThat(response.getBody().getName()).isEqualTo(updatedProduct.getName());
//    }
//
//    @Test
//    public void shouldFailToUpdateProductAsClient() {
//        // given
//        ProductDto updatedProduct = ProductDto.builder()
//                .name("Updated Product")
//                .description("Updated Description")
//                .price(BigDecimal.valueOf(299.99))
//                .stockQuantity(30)
//                .category("Updated Category")
//                .build();
//
//        // when
//        ResponseEntity<Map<String, String>> response = executePut(
//                PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
//                updatedProduct,
//                getHeadersWith(clientToken),
//                mapTypeReference());
//
//        // then
//        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
//    }
//
//}