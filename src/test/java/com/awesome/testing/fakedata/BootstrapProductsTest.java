package com.awesome.testing.fakedata;

import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapProductsTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void shouldCreateBootstrapProductsWhenCatalogIsEmpty() throws Exception {
        when(productRepository.count()).thenReturn(0L);

        BootstrapProducts bootstrapProducts = bootstrapProducts("""
                [
                  {
                    "name": "Demo Product",
                    "description": "Demo description",
                    "price": 19.99,
                    "stockQuantity": 4,
                    "category": "Demo",
                    "imageUrl": "/images/demo.png"
                  }
                ]
                """);

        bootstrapProducts.run();

        ArgumentCaptor<Iterable<ProductEntity>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(productRepository).saveAll(captor.capture());
        List<ProductEntity> saved = (List<ProductEntity>) captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getName()).isEqualTo("Demo Product");
        assertThat(saved.getFirst().getCategory()).isEqualTo("Demo");
        assertThat(saved.getFirst().getImageUrl()).isEqualTo("/images/demo.png");
    }

    @Test
    void shouldSkipBootstrapWhenCatalogAlreadyContainsProducts() throws Exception {
        when(productRepository.count()).thenReturn(2L);

        BootstrapProducts bootstrapProducts = bootstrapProducts("[]");

        bootstrapProducts.run();

        verify(productRepository, never()).saveAll(anyIterable());
    }

    @Test
    void shouldFailWhenBootstrapCatalogIsEmpty() {
        when(productRepository.count()).thenReturn(0L);

        BootstrapProducts bootstrapProducts = bootstrapProducts("[]");

        assertThatThrownBy(() -> bootstrapProducts.run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Bootstrap product catalog is empty");
    }

    private BootstrapProducts bootstrapProducts(String json) {
        ProductCatalogLoader loader = new ProductCatalogLoader(
                new ObjectMapper(),
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8))
        );
        return new BootstrapProducts(productRepository, loader);
    }
}
