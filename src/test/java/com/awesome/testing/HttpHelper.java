package com.awesome.testing;

import com.awesome.testing.config.TestConfig;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestConfig.class)
@ActiveProfiles("test")
public abstract class HttpHelper {

    @LocalServerPort
    private int port;

    private RestClient restClient;

    private RestClient restClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl("http://localhost:" + port)
                    .defaultStatusHandler(HttpStatusCode::isError, (req, res) -> {
                    })
                    .build();
        }
        return restClient;
    }

    protected <T> ResponseEntity<T> executeGet(String url,
                                               HttpHeaders httpHeaders,
                                               Class<T> responseType) {
        return execute(HttpMethod.GET, url, null, httpHeaders, responseType);
    }

    protected <T> ResponseEntity<T> executeGet(String url,
                                               HttpHeaders httpHeaders,
                                               ParameterizedTypeReference<T> responseType) {
        return execute(HttpMethod.GET, url, null, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> executePut(String url, V body, HttpHeaders httpHeaders, Class<T> responseType) {
        return execute(HttpMethod.PUT, url, body, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> executePut(String url, V body, HttpHeaders httpHeaders, ParameterizedTypeReference<T> responseType) {
        return execute(HttpMethod.PUT, url, body, httpHeaders, responseType);
    }

    protected <T> ResponseEntity<T> executeDelete(String url, HttpHeaders httpHeaders, Class<T> responseType) {
        return execute(HttpMethod.DELETE, url, null, httpHeaders, responseType);
    }

    protected <T> ResponseEntity<T> executeDelete(String url, HttpHeaders httpHeaders, ParameterizedTypeReference<T> responseType) {
        return execute(HttpMethod.DELETE, url, null, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> executePost(String url, V body, HttpHeaders httpHeaders, Class<T> responseType) {
        return execute(HttpMethod.POST, url, body, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> executePatch(String url, V body, HttpHeaders httpHeaders, Class<T> responseType) {
        return execute(HttpMethod.PATCH, url, body, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> executePost(String url, V body, HttpHeaders httpHeaders, ParameterizedTypeReference<T> responseType) {
        return execute(HttpMethod.POST, url, body, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> execute(HttpMethod httpMethod,
                                               String url,
                                               V body,
                                               HttpHeaders httpHeaders,
                                               Class<T> responseType) {
        return prepareRequest(httpMethod, url, body, httpHeaders)
                .retrieve()
                .toEntity(responseType);
    }

    protected <T, V> ResponseEntity<T> execute(HttpMethod httpMethod,
                                               String url,
                                               V body,
                                               HttpHeaders httpHeaders,
                                               ParameterizedTypeReference<T> responseType) {
        return prepareRequest(httpMethod, url, body, httpHeaders)
                .retrieve()
                .toEntity(responseType);
    }

    private <V> RestClient.RequestBodySpec prepareRequest(HttpMethod httpMethod,
                                                          String url,
                                                          V body,
                                                          HttpHeaders httpHeaders) {
        RestClient.RequestBodySpec request = restClient()
                .method(httpMethod)
                .uri(url)
                .headers(headers -> headers.addAll(httpHeaders));
        if (body != null) {
            request.body(body);
        }
        return request;
    }

    protected HttpHeaders getJsonOnlyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

}
