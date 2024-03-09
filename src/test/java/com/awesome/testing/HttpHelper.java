package com.awesome.testing;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class HttpHelper {

    @Autowired
    protected TestRestTemplate restTemplate;

    protected <T> ResponseEntity<T> executeGet(String url,
                                               HttpHeaders httpHeaders,
                                               Class<T> responseType) {
        return execute(HttpMethod.GET, url, null, httpHeaders, responseType);
    }

    protected ResponseEntity<Object> executePut(String url, Object body, HttpHeaders httpHeaders) {
        return execute(HttpMethod.PUT, url, body, httpHeaders, Object.class);
    }

    protected <T> ResponseEntity<T> executePut(String url, Object body, HttpHeaders httpHeaders, Class<T> responseType) {
        return execute(HttpMethod.PUT, url, body, httpHeaders, responseType);
    }

    protected ResponseEntity<?> executeDelete(String url, HttpHeaders httpHeaders) {
        return execute(HttpMethod.DELETE, url, null, httpHeaders, Object.class);
    }

    protected <T> ResponseEntity<T> executeDelete(String url, HttpHeaders httpHeaders, Class<T> responseType) {
        return execute(HttpMethod.DELETE, url, null, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> executePost(String url, V body, HttpHeaders httpHeaders, Class<T> responseType) {
        return execute(HttpMethod.POST, url, body, httpHeaders, responseType);
    }

    protected <T, V> ResponseEntity<T> execute(HttpMethod httpMethod,
                                               String url,
                                               V body,
                                               HttpHeaders httpHeaders,
                                               Class<T> responseType) {
        return restTemplate.exchange(url,
                httpMethod,
                new HttpEntity<>(body, httpHeaders),
                responseType);
    }

    protected HttpHeaders getJsonOnlyHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    protected HttpHeaders getImageHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, MediaType.IMAGE_PNG_VALUE);
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

}
