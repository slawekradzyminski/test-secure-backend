package com.awesome.testing.service;

import com.awesome.testing.dto.product.ProductSummaryDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.repository.ProductRepository;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ProductPaginationTest {

    @Autowired
    private ProductRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private ProductService service;
    private final List<Long> ids = new ArrayList<>();

    @BeforeEach
    void seedCatalog() {
        service = new ProductService(repository, null);
        for (int i = 0; i < 2000; i++) {
            ids.add(entityManager.persist(ProductEntity.builder()
                    .name("Product " + i)
                    .description("Catalog description")
                    .price(BigDecimal.TEN)
                    .stockQuantity(i % 2)
                    .category(i % 4 == 1 ? "Electronics" : "Other")
                    .build()).getId());
        }
        entityManager.flush();
        entityManager.clear();
        statistics().clear();
    }

    @Test
    void deepOffsetLoadsOnlyRequestedProducts() {
        var result = service.listProducts(1903, 25, null, null);

        assertThat(result.getProducts()).extracting(ProductSummaryDto::getId)
                .containsExactlyElementsOf(ids.subList(1903, 1928));
        assertThat(result.getTotal()).isEqualTo(2000);
        assertThat(result.getPage()).isEqualTo(1903);
        assertThat(result.getSize()).isEqualTo(25);
        System.out.printf("Product pagination: offset=1903 limit=25 loaded=%d queries=%d%n",
                statistics().getEntityLoadCount(), statistics().getPrepareStatementCount());
        assertThat(statistics().getEntityLoadCount()).isEqualTo(25);
        assertThat(statistics().getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void filtersBeforeApplyingOffsetAndCountsAllMatches() {
        var result = service.listProducts(3, 4, "eLECTRONICS", true);

        assertThat(result.getProducts()).extracting(ProductSummaryDto::getId)
                .containsExactly(ids.get(13), ids.get(17), ids.get(21), ids.get(25));
        assertThat(result.getTotal()).isEqualTo(500);
        assertThat(statistics().getEntityLoadCount()).isEqualTo(4);
    }

    @Test
    void handlesPartialLastPageAndOffsetBeyondEnd() {
        var last = service.listProducts(1997, 25, " ", false);
        assertThat(last.getProducts()).extracting(ProductSummaryDto::getId)
                .containsExactlyElementsOf(ids.subList(1997, 2000));
        assertThat(last.getTotal()).isEqualTo(2000);

        var empty = service.listProducts(Integer.MAX_VALUE, 100, null, null);
        assertThat(empty.getProducts()).isEmpty();
        assertThat(empty.getTotal()).isEqualTo(2000);
        assertThat(empty.getPage()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void clampsOffsetAndPageSize() {
        var first = service.listProducts(-1, 0, null, null);
        assertThat(first.getProducts()).extracting(ProductSummaryDto::getId).containsExactly(ids.getFirst());
        assertThat(first.getPage()).isZero();
        assertThat(first.getSize()).isEqualTo(1);

        var capped = service.listProducts(0, Integer.MAX_VALUE, null, null);
        assertThat(capped.getProducts()).hasSize(100);
        assertThat(capped.getSize()).isEqualTo(100);
    }

    private org.hibernate.stat.Statistics statistics() {
        return entityManager.getEntityManager().getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
    }
}
