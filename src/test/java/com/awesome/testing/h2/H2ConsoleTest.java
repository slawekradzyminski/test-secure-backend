package com.awesome.testing.h2;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;

import com.awesome.testing.DomainHelper;

public class H2ConsoleTest extends DomainHelper {

    @Value("${spring.datasource.url}")
    private String url;
    
    @Value("${spring.datasource.username}")
    private String user;
    
    @Value("${spring.datasource.password}")
    private String password;
    
    @Test
    public void shouldDisplayH2Console() {
        // when
        ResponseEntity<String> responseWithToken =
                executeGet("/h2-console", new HttpHeaders(), String.class);

        // then
        assertThat(responseWithToken.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    public void shouldLoginToH2Console() {
        // given successful login
        ResponseEntity<String> responseWithToken =
                executeGet("/h2-console", new HttpHeaders(), String.class);
        String jsessionid = extractSessionId(responseWithToken.getBody());
        restTemplate.postForEntity(
                "/h2-console/login.do?jsessionid=" + jsessionid, getLoginRequest(), String.class);

        // when request for tables
        ResponseEntity<String> tablesResponse =
                executeGet("/h2-console/tables.do?jsessionid=" + jsessionid, new HttpHeaders(), String.class);

        // then
        assertThat(tablesResponse.getBody()).contains(
                "'table', 'SLOTS',",
                "'table', 'SPECIALTIES'",
                "'table', 'USERS'"
        );
    }

    @Test
    public void shouldFailToLoginToH2Console() {
        // given invalid login
        ResponseEntity<String> responseWithToken =
                executeGet("/h2-console", new HttpHeaders(), String.class);
        String jsessionid = extractSessionId(responseWithToken.getBody());
        restTemplate.postForEntity(
                "/h2-console/login.do?jsessionid=" + jsessionid, getInvalidLoginRequest(), String.class);

        // when request for tables
        ResponseEntity<String> tablesResponse =
                executeGet("/h2-console/tables.do?jsessionid=" + jsessionid, new HttpHeaders(), String.class);

        // then
        assertThat(tablesResponse.getBody()).doesNotContain(
                "'table', 'SLOTS',",
                "'table', 'DOCTOR_TYPES'",
                "'table', 'USERS'"
        );
        assertThat(tablesResponse.getBody()).contains("java.lang.NullPointerException");
    }

    private HttpEntity<MultiValueMap<String, String>> getInvalidLoginRequest() {
        return new HttpEntity<>(getFormData("wrong"), formUrlEncodedHeaders());
    }

    private HttpEntity<MultiValueMap<String, String>> getLoginRequest() {
        return new HttpEntity<>(getFormData(user), formUrlEncodedHeaders());
    }

    private HttpHeaders formUrlEncodedHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return headers;
    }

    private MultiValueMap<String, String> getFormData(String username) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("language", "en");
        formData.add("setting", "Generic H2 (Embedded)");
        formData.add("name", "Generic H2 (Embedded)");
        formData.add("driver", "org.h2.Driver");
        formData.add("url", url);
        formData.add("user", username);
        formData.add("password", password);
        return formData;
    }

    @SuppressWarnings("ConstantConditions")
    private String extractSessionId(String body) {
        Document doc = Jsoup.parse(body);
        Element scriptElement = doc.select("script").first();
        String scriptText = scriptElement.data();
        String url = scriptText.substring(scriptText.indexOf('\'') + 1, scriptText.lastIndexOf('\''));
        return url.substring(url.indexOf('=') + 1);
    }

}
