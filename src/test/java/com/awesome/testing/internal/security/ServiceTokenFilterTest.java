package com.awesome.testing.internal.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ServiceTokenFilterTest {

    private final ServiceTokenFilter filter = new ServiceTokenFilter(new ServiceTokenGuard("token-1"));

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
        ServiceTokenFilter disabled = new ServiceTokenFilter(new ServiceTokenGuard(""));
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
    }

    private static MockHttpServletRequest request(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRequestURI(uri);
        return request;
    }
}
