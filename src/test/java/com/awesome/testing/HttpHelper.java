package com.awesome.testing;

import com.awesome.testing.dto.LoginDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.text.MessageFormat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class HttpHelper {

    @Autowired
    protected TestRestTemplate restTemplate;

    protected <T> ResponseEntity<T> executeGet(String url,
                                               HttpHeaders httpHeaders,
                                               Class<T> responseType) {
        return execute(HttpMethod.GET, url, null, httpHeaders, responseType);
    }

    protected void executePut(String url, Object body, HttpHeaders httpHeaders) {
        execute(HttpMethod.PUT, url, body, httpHeaders, Object.class);
    }

    protected void executeDelete(String url, HttpHeaders httpHeaders) {
        execute(HttpMethod.DELETE, url, null, httpHeaders, Object.class);
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

    protected HttpHeaders getAdminHeaders() {
        HttpHeaders headers = getUnauthorizedHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, getToken("admin", "admin"));
        return headers;
    }

    protected HttpHeaders getClientHeaders() {
        HttpHeaders headers = getUnauthorizedHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, getToken("client", "client"));
        return headers;
    }

    protected HttpHeaders getUnauthorizedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    private String getToken(String username, String password) {
        LoginDto loginDetails = new LoginDto(username, password);

        String token = restTemplate.exchange(
                "/users/signin",
                HttpMethod.POST,
                new HttpEntity<>(loginDetails, getUnauthorizedHeaders()),
                String.class)
                .getBody();

        return MessageFormat.format("Bearer {0}", token);
    }

}
