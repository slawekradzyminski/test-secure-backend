package com.awesome.testing.graphql;

import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.service.CartService;
import com.awesome.testing.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles({"test", "graphql"})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:graphqltests;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
abstract class AbstractCommerceGraphQlTest extends AbstractEcommerceTest {
    protected static final String ENDPOINT = "/api/v1/graphql";
    protected static final AddressDto ADDRESS = new AddressDto("Main Street 1", "Warsaw", "Mazovia", "00-001", "Poland");
    protected static final Map<String, Object> ADDRESS_INPUT = Map.of(
            "street", "Main Street 1", "city", "Warsaw", "state", "Mazovia", "zipCode", "00-001", "country", "Poland");

    @Autowired
    protected ObjectMapper mapper;
    @Autowired
    protected CartService cartService;
    @Autowired
    protected OrderService orderService;

    protected Actor customer() {
        return actor(Role.ROLE_CLIENT);
    }

    protected Actor admin() {
        return actor(Role.ROLE_ADMIN);
    }

    private Actor actor(Role role) {
        var user = getRandomUserWithRoles(List.of(role));
        return new Actor(user.getUsername(), getToken(user));
    }

    protected ProductEntity product(String price, int stock) {
        var product = setupProduct();
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        return productRepository.saveAndFlush(product);
    }

    protected OrderDto givenOrder(Actor customer, ProductEntity product) {
        cartService.addToCart(customer.username(), new CartItemDto(product.getId(), 1));
        return orderService.createOrder(customer.username(), ADDRESS);
    }

    protected JsonNode query(String token, String document, Map<String, Object> variables) {
        var response = executePost(ENDPOINT, Map.of("query", document, "variables", variables),
                getHeadersWith(token), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody());
    }

    protected JsonNode successful(Actor actor, String document, Map<String, Object> variables) {
        var response = query(actor.token(), document, variables);
        assertThat(response.path("errors").isMissingNode()).as(response.toString()).isTrue();
        return response.path("data");
    }

    protected void assertError(JsonNode response, String code) {
        assertThat(response.at("/errors/0/extensions/code").asText()).isEqualTo(code);
    }

    protected record Actor(String username, String token) { }
}
