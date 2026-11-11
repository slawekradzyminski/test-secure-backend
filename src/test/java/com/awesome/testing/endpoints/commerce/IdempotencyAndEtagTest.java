package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.order.AddressDto;
import com.awesome.testing.dto.order.OrderDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.entity.ProductEntity;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static com.awesome.testing.factory.AddressFactory.getRandomAddress;
import static org.assertj.core.api.Assertions.assertThat;

class IdempotencyAndEtagTest extends AbstractCommerceFlowTest {

    @Test
    void repeatingAnIdempotencyKeyReplaysTheOrderWithoutChargingStockTwice() {
        ProductEntity product = product("10.00", 5);
        addToCart(clientToken, product, 2);
        AddressDto address = getRandomAddress();

        ResponseEntity<OrderDto> first = checkoutWithKey(address, "retry-key-0001", OrderDto.class);
        ResponseEntity<OrderDto> replay = checkoutWithKey(address, "retry-key-0001", OrderDto.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(first.getHeaders().containsHeader("Idempotent-Replayed")).isFalse();
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(replay.getHeaders().getFirst("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.getBody().getId()).isEqualTo(first.getBody().getId());
        assertThat(stockOf(product)).isEqualTo(3);
        assertThat(orderRepository.count()).isEqualTo(1);
    }

    @Test
    void reusingAKeyWithADifferentAddressIsAConflictAndKeysAreScopedPerUser() {
        ProductEntity product = product("10.00", 5);
        addToCart(clientToken, product, 1);
        checkoutWithKey(getRandomAddress(), "shared-key-01", OrderDto.class);
        addToCart(clientToken, product, 1);
        addToCart(otherClientToken, product, 1);

        ResponseEntity<ErrorDto> conflict = checkoutWithKey(getRandomAddress(), "shared-key-01", ErrorDto.class);
        ResponseEntity<OrderDto> otherUser = executePost(ORDERS_ENDPOINT, getRandomAddress(),
                withKey(otherClientToken, "shared-key-01"), OrderDto.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(otherUser.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(otherUser.getHeaders().containsHeader("Idempotent-Replayed")).isFalse();
    }

    @Test
    void malformedIdempotencyKeyIsRejectedBeforeTheCartIsTouched() {
        ProductEntity product = product("10.00", 5);
        addToCart(clientToken, product, 1);

        ResponseEntity<ErrorDto> response = checkoutWithKey(getRandomAddress(), "short", ErrorDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(stockOf(product)).isEqualTo(5);
    }

    @Test
    void productReadsCarryAnEtagThatSupportsConditionalGets() {
        ProductEntity product = product("10.00", 5);

        ResponseEntity<ProductDto> first = executeGet(PRODUCTS_ENDPOINT + "/" + product.getId(), getHeadersWith(clientToken), ProductDto.class);
        HttpHeaders conditional = getHeadersWith(clientToken);
        conditional.setIfNoneMatch(first.getHeaders().getETag());
        ResponseEntity<ProductDto> notModified = executeGet(PRODUCTS_ENDPOINT + "/" + product.getId(), conditional, ProductDto.class);

        assertThat(first.getHeaders().getETag()).isEqualTo("\"" + productRepository.findById(product.getId()).orElseThrow().getVersion() + "\"");
        assertThat(notModified.getStatusCode()).isEqualTo(HttpStatus.NOT_MODIFIED);
        assertThat(notModified.getBody()).isNull();
    }

    @Test
    void staleIfMatchIsRejectedAndCurrentIfMatchReturnsTheNewEtag() {
        ProductEntity product = product("10.00", 5);
        String etag = executeGet(PRODUCTS_ENDPOINT + "/" + product.getId(), getHeadersWith(adminToken), ProductDto.class)
                .getHeaders().getETag();

        ResponseEntity<ProductDto> firstEdit = update(product, etag, "11.00", ProductDto.class);
        ResponseEntity<ErrorDto> lostUpdate = update(product, etag, "12.00", ErrorDto.class);
        ResponseEntity<ProductDto> unconditional = update(product, null, "13.00", ProductDto.class);

        assertThat(firstEdit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstEdit.getHeaders().getETag()).isNotEqualTo(etag);
        assertThat(lostUpdate.getStatusCode()).isEqualTo(HttpStatus.PRECONDITION_FAILED);
        assertThat(unconditional.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getPrice()).isEqualByComparingTo("13.00");
    }

    @Test
    void browsersOnAnotherOriginMayUseTheConditionalAndIdempotencyHeaders() {
        HttpHeaders preflight = new HttpHeaders();
        preflight.setOrigin("http://localhost:8081");
        preflight.setAccessControlRequestMethod(org.springframework.http.HttpMethod.POST);
        preflight.setAccessControlRequestHeaders(java.util.List.of("authorization", "content-type", "idempotency-key", "if-match"));

        ResponseEntity<String> response = execute(org.springframework.http.HttpMethod.OPTIONS, ORDERS_ENDPOINT, null, preflight, String.class);
        HttpHeaders read = getHeadersWith(clientToken);
        read.setOrigin("http://localhost:8081");
        ResponseEntity<ProductDto> product = executeGet(PRODUCTS_ENDPOINT + "/" + product("10.00", 1).getId(), read, ProductDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowHeaders())
                .map(String::toLowerCase)
                .contains("idempotency-key", "if-match");
        assertThat(product.getHeaders().getAccessControlExposeHeaders())
                .contains("ETag", "Idempotent-Replayed", "Content-Disposition");
    }

    @Test
    void browsersOnAnotherOriginMaySendAndReadTheCorrelationHeaders() {
        HttpHeaders preflight = new HttpHeaders();
        preflight.setOrigin("http://localhost:8081");
        preflight.setAccessControlRequestMethod(org.springframework.http.HttpMethod.GET);
        preflight.setAccessControlRequestHeaders(java.util.List.of("x-request-id", "traceparent"));

        ResponseEntity<String> response = execute(org.springframework.http.HttpMethod.OPTIONS, PRODUCTS_ENDPOINT, null, preflight, String.class);
        HttpHeaders read = getHeadersWith(clientToken);
        read.setOrigin("http://localhost:8081");
        read.add("X-Request-Id", "browser-req-1");
        ResponseEntity<String> products = executeGet(PRODUCTS_ENDPOINT, read, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowHeaders())
                .map(String::toLowerCase)
                .contains("x-request-id", "traceparent");
        assertThat(products.getHeaders().getAccessControlExposeHeaders()).contains("X-Request-Id");
        assertThat(products.getHeaders().getFirst("X-Request-Id")).isEqualTo("browser-req-1");
    }

    private <T> ResponseEntity<T> update(ProductEntity product, String ifMatch, String price, Class<T> type) {
        HttpHeaders headers = getHeadersWith(adminToken);
        if (ifMatch != null) {
            headers.setIfMatch(ifMatch);
        }
        return executePut(PRODUCTS_ENDPOINT + "/" + product.getId(),
                ProductUpdateDto.builder().price(new BigDecimal(price)).build(), headers, type);
    }

    private <T> ResponseEntity<T> checkoutWithKey(AddressDto address, String key, Class<T> type) {
        return executePost(ORDERS_ENDPOINT, address, withKey(clientToken, key), type);
    }

    private HttpHeaders withKey(String token, String key) {
        HttpHeaders headers = getHeadersWith(token);
        headers.add("Idempotency-Key", key);
        return headers;
    }
}
