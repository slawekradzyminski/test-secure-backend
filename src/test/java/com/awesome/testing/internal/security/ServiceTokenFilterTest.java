package com.awesome.testing.internal.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ServiceTokenFilterTest {

    private static final InternalRoutes ROUTES = InternalRoutes.builder()
            .route("/internal/v1/orders/\\d+/payment", ServiceCaller.PAYMENT)
            .otherwise(ServiceCaller.WAREHOUSE)
            .build();

    private final ServiceTokenFilter filter = new ServiceTokenFilter(
            new ServiceTokenGuard(Map.of(ServiceCaller.WAREHOUSE, "token-1", ServiceCaller.PAYMENT, "pay-1")), ROUTES);

    @Test
    void passesAcceptedRequestsDownTheChain() throws Exception {
        MockHttpServletRequest request = request("/internal/v1/products");
        request.addHeader("X-Service-Token", "token-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsMissingAndWrongTokensWithUnauthorized() throws Exception {
        MockHttpServletRequest wrong = request("/internal/v1/orders/1");
        wrong.addHeader("X-Service-Token", "token-2");
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilter(request("/internal/v1/orders/1"), missingResponse, chain);
        MockHttpServletResponse wrongResponse = new MockHttpServletResponse();
        filter.doFilter(wrong, wrongResponse, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(missingResponse.getStatus()).isEqualTo(401);
        assertThat(wrongResponse.getStatus()).isEqualTo(401);
        assertThat(wrongResponse.getContentAsString()).contains("Invalid service token");
    }

    @Test
    void respondsNotFoundWhenDisabled() throws Exception {
        ServiceTokenFilter disabled = new ServiceTokenFilter(new ServiceTokenGuard(Map.of()), ROUTES);
        MockHttpServletRequest request = request("/internal/v1/products");
        request.addHeader("X-Service-Token", "");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        disabled.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentAsString()).contains("Not found");
    }

    @Test
    void eachPathAcceptsOnlyItsCallersToken() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse paymentOnWarehouse = new MockHttpServletResponse();
        filter.doFilter(withToken("/internal/v1/orders/1", "pay-1"), paymentOnWarehouse, chain);
        MockHttpServletResponse warehouseOnPayment = new MockHttpServletResponse();
        filter.doFilter(withToken("/internal/v1/orders/1/payment", "token-1"), warehouseOnPayment, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(paymentOnWarehouse.getStatus()).isEqualTo(401);
        assertThat(warehouseOnPayment.getStatus()).isEqualTo(401);

        MockHttpServletRequest payment = withToken("/internal/v1/orders/1/payment", "pay-1");
        MockHttpServletResponse paymentResponse = new MockHttpServletResponse();
        filter.doFilter(payment, paymentResponse, chain);
        verify(chain).doFilter(payment, paymentResponse);
    }

    @Test
    void aDisabledCallerHidesOnlyItsOwnPaths() throws Exception {
        ServiceTokenFilter warehouseOnly = new ServiceTokenFilter(
                new ServiceTokenGuard(Map.of(ServiceCaller.WAREHOUSE, "token-1")), ROUTES);
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse payment = new MockHttpServletResponse();
        warehouseOnly.doFilter(withToken("/internal/v1/orders/1/payment", "token-1"), payment, chain);
        MockHttpServletResponse warehouse = new MockHttpServletResponse();
        warehouseOnly.doFilter(withToken("/internal/v1/orders/1", "wrong"), warehouse, chain);

        assertThat(payment.getStatus()).isEqualTo(404);
        assertThat(warehouse.getStatus()).isEqualTo(401);
    }

    @Test
    void ignoresPathsOutsideTheInternalApiIncludingLookalikes() throws Exception {
        for (String path : new String[] {"/api/v1/products", "/internals/x", "/api/internal/v1"}) {
            MockHttpServletRequest request = request(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            FilterChain chain = mock(FilterChain.class);

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(request, response);
        }
    }

    @Test
    void stripsTheContextPathBeforeMatching() throws Exception {
        MockHttpServletRequest request = request("/app/internal/v1/products");
        request.setContextPath("/app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(401);

        MockHttpServletRequest payment = withToken("/app/internal/v1/orders/3/payment", "pay-1");
        payment.setContextPath("/app");
        MockHttpServletResponse paymentResponse = new MockHttpServletResponse();
        filter.doFilter(payment, paymentResponse, chain);
        verify(chain).doFilter(payment, paymentResponse);
    }

    private static MockHttpServletRequest withToken(String uri, String token) {
        MockHttpServletRequest request = request(uri);
        request.addHeader("X-Service-Token", token);
        return request;
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
