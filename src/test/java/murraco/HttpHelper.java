package murraco;

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

    protected <T> ResponseEntity<T> executeGet(String url, Class<T> responseType) {
        return execute(HttpMethod.GET, url, null, responseType);
    }

    protected void executePut(String url, Object body) {
        execute(HttpMethod.PUT, url, body, Object.class);
    }

    protected void executeDelete(String url) {
        execute(HttpMethod.DELETE, url, null, Object.class);
    }

    protected <T, V> ResponseEntity<T> executePost(String url, V body, Class<T> responseType) {
        return execute(HttpMethod.POST, url, body, responseType);
    }

    protected <T, V> ResponseEntity<T> execute(HttpMethod httpMethod, String url, V body, Class<T> responseType) {
        return restTemplate.exchange(url,
                httpMethod,
                new HttpEntity<>(body, getRequiredHeaders()),
                responseType);
    }

    private HttpHeaders getRequiredHeaders() {
        HttpHeaders headers = getUnauthorizedHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, getToken());
        return headers;
    }

    protected HttpHeaders getUnauthorizedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return headers;
    }

    private String getToken() {
        String token = restTemplate.exchange(
                "/users/signin?password=admin&username=admin",
                HttpMethod.POST,
                new HttpEntity<>(getUnauthorizedHeaders()),
                String.class)
                .getBody();

        return MessageFormat.format("Bearer {0}", token);
    }

}
