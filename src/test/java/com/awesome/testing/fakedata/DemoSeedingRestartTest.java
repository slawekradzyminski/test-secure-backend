package com.awesome.testing.fakedata;

import static com.awesome.testing.factory.ProductFactory.getRandomProductCreate;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

import com.awesome.testing.DomainHelper;
import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.CartItemRepository;
import com.awesome.testing.repository.OrderRepository;
import com.awesome.testing.repository.ProductRepository;
import com.awesome.testing.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Catalog X-01: the seeding profiles run {@link SetupData} on every start, so the data users left behind
 * must never stop the backend from starting. Runs on its own database because it seeds demo data.
 */
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:demoseedingrestart;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
class DemoSeedingRestartTest extends DomainHelper {

    @Autowired private SetupUsers setupUsers;
    @Autowired private SetupProducts setupProducts;
    @Autowired private SetupOrders setupOrders;
    @Autowired private SetupCommerce setupCommerce;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper mapper;

    @Test
    void startsAgainWhenAStoredProductHasNoImageUrl() {
        // given: a first start seeded the demo data, then a product row was stored without an image URL
        seedEmptyCatalogue();
        ProductEntity withoutImage = productRepository.findAll().getFirst();
        jdbcTemplate.update("update products set image_url = null where id = ?", withoutImage.getId());

        // when: the next start runs the seeding again
        setupData().setupData();

        // then
        assertThat(productRepository.findById(withoutImage.getId()).orElseThrow().getImageUrl()).isEmpty();
        assertThat(jdbcTemplate.queryForObject("select count(*) from products where image_url is null", Long.class))
                .isZero();
    }

    @Test
    void startsAgainWhenADeletedDemoAccountsEmailBelongsToAnotherAccount() {
        // given
        seedEmptyCatalogue();
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        userRepository.delete(userRepository.findByUsername("client").orElseThrow());
        userRepository.save(UserEntity.builder().username("alice-new").email("alice.smith@yahoo.com")
                .password("encoded-password-value").roles(List.of(Role.ROLE_CLIENT)).build());

        // when
        setupData().setupData();

        // then
        assertThat(userRepository.findByUsername("client")).isEmpty();
        assertThat(orderRepository.findAll()).isNotEmpty()
                .allSatisfy(order -> assertThat(order.getUsername()).isNotEqualTo("client"));
    }

    @Test
    void startsAgainWhenADemoProductWasDeletedAndTheOrdersAreGone() {
        // given
        seedEmptyCatalogue();
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.delete(
                productRepository.findFirstByNameIgnoreCaseOrderByIdAsc("PlayStation 5").orElseThrow());

        // when
        setupData().setupData();

        // then: the two demo orders of the deleted product are skipped, the other four are created
        assertThat(orderRepository.count()).isEqualTo(4);
    }

    @Test
    void storesAProductCreatedWithoutAnImageUrlWithAnEmptyImageUrl() {
        // given
        String adminToken = getToken(getRandomUserWithRoles(List.of(Role.ROLE_ADMIN)));
        ProductCreateDto product = getRandomProductCreate();
        product.setImageUrl(null);

        // when
        var response = executePost("/api/v1/products", product, getHeadersWith(adminToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long id = mapper.readTree(response.getBody()).path("id").asLong();
        assertThat(mapper.readTree(response.getBody()).path("imageUrl").isString()).isTrue();
        assertThat(jdbcTemplate.queryForObject("select image_url from products where id = ?", String.class, id))
                .isEmpty();
    }

    private void seedEmptyCatalogue() {
        cartItemRepository.deleteAll();
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.findByUsername("alice-new").ifPresent(userRepository::delete);
        setupData().setupData();
    }

    private SetupData setupData() {
        SetupData setupData = new SetupData(setupUsers, setupProducts, setupOrders, setupCommerce);
        ReflectionTestUtils.setField(setupData, "enabled", true);
        return setupData;
    }
}
