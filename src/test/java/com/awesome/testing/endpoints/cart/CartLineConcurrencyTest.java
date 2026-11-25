package com.awesome.testing.endpoints.cart;

import com.awesome.testing.dto.cart.CartItemDto;
import com.awesome.testing.dto.cart.UpdateCartItemDto;
import com.awesome.testing.dto.user.Role;
import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.endpoints.AbstractEcommerceTest;
import com.awesome.testing.entity.CartItemEntity;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntFunction;

import static com.awesome.testing.factory.CartItemFactory.getSingleCartItemFrom;
import static com.awesome.testing.factory.ProductFactory.getRandomProduct;
import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parallel edits of one cart line must never surface as a 500: they either succeed one after
 * another or get the documented 409, and no successful edit is lost.
 */
class CartLineConcurrencyTest extends AbstractEcommerceTest {

    private static final int PARALLEL_REQUESTS = 8;
    private static final Set<HttpStatusCode> ALLOWED_STATUSES = Set.of(HttpStatus.OK, HttpStatus.CONFLICT);

    @Autowired
    private CartService cartService;

    @Test
    void parallelQuantityUpdatesOfOneLineReturnOnly200Or409() throws Exception {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        HttpHeaders headers = getHeadersWith(getToken(client));
        ProductEntity product = productWithStock();
        cartService.addToCart(client.getUsername(), getSingleCartItemFrom(product.getId()));

        // when
        List<HttpStatusCode> statuses = runInParallel(i -> () -> executePut(
                CART_ITEMS_ENDPOINT + "/" + product.getId(),
                UpdateCartItemDto.builder().quantity(i + 1).build(),
                headers,
                String.class).getStatusCode());

        // then
        assertThat(statuses).allMatch(ALLOWED_STATUSES::contains);
        assertThat(statuses).contains(HttpStatus.OK);
        assertThat(cartLine(client, product).getQuantity()).isBetween(1, PARALLEL_REQUESTS);
    }

    @Test
    void parallelAddsToAnExistingLineReturnOnly200Or409AndLoseNoQuantity() throws Exception {
        // given
        UserRegisterDto client = getRandomUserWithRoles(List.of(Role.ROLE_CLIENT));
        HttpHeaders headers = getHeadersWith(getToken(client));
        ProductEntity product = productWithStock();
        CartItemDto oneMore = getSingleCartItemFrom(product.getId());
        cartService.addToCart(client.getUsername(), oneMore);

        // when
        List<HttpStatusCode> statuses = runInParallel(i -> () -> executePost(
                CART_ITEMS_ENDPOINT,
                oneMore,
                headers,
                String.class).getStatusCode());

        // then
        assertThat(statuses).allMatch(ALLOWED_STATUSES::contains);
        long succeeded = statuses.stream().filter(HttpStatus.OK::equals).count();
        assertThat(succeeded).isPositive();
        assertThat(cartLine(client, product).getQuantity()).isEqualTo(1 + (int) succeeded);
    }

    private List<HttpStatusCode> runInParallel(IntFunction<Callable<HttpStatusCode>> request) throws Exception {
        CountDownLatch ready = new CountDownLatch(PARALLEL_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS)) {
            List<Future<HttpStatusCode>> futures = new ArrayList<>();
            for (int i = 0; i < PARALLEL_REQUESTS; i++) {
                Callable<HttpStatusCode> call = request.apply(i);
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, SECONDS)).isTrue();
                    return call.call();
                }));
            }
            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();
            List<HttpStatusCode> statuses = new ArrayList<>();
            for (Future<HttpStatusCode> future : futures) {
                statuses.add(future.get(30, SECONDS));
            }
            return statuses;
        }
    }

    private ProductEntity productWithStock() {
        ProductEntity product = getRandomProduct();
        product.setStockQuantity(10 * PARALLEL_REQUESTS);
        return productRepository.save(product);
    }

    private CartItemEntity cartLine(UserRegisterDto client, ProductEntity product) {
        return cartItemRepository.findByUsernameAndProductId(client.getUsername(), product.getId()).orElseThrow();
    }
}
