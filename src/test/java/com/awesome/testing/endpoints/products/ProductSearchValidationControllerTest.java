package com.awesome.testing.endpoints.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.stream.IntStream;

import static com.awesome.testing.util.TypeReferenceUtil.mapTypeReference;
import static org.assertj.core.api.Assertions.assertThat;

class ProductSearchValidationControllerTest extends AbstractProductSearchTest {

    @Test
    void shouldGet401WhenNotAuthenticated() {
        // when
        ResponseEntity<Map<String, String>> response =
                executeGet(SEARCH_ENDPOINT + "?q=lamp", getJsonOnlyHeaders(), mapTypeReference());

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("message", "Unauthorized");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
        "?minPrice=20&maxPrice=10 | minPrice  | must not be greater than maxPrice",
        "?minPrice=-0.01          | minPrice  | must be zero or greater",
        "?maxPrice=-1             | maxPrice  | must be zero or greater",
        "?minRating=0.99          | minRating | must be between 1 and 5",
        "?minRating=5.01          | minRating | must be between 1 and 5",
        "?sort=cheapest           | sort      | must be one of relevance, newest, price_asc, price_desc, name_asc, rating_desc",
        "?page=-1                 | page      | must be zero or greater",
        "?size=0                  | size      | must be between 1 and 100",
        "?size=101                | size      | must be between 1 and 100",
        "?minPrice=cheap          | minPrice  | must be a number",
        "?minRating=five          | minRating | must be a number",
        "?inStock=maybe           | inStock   | must be true or false",
        "?page=first              | page      | must be an integer",
        "?size=1.5                | size      | must be an integer"
    })
    void shouldGet400ForInvalidParameter(String queryString, String parameter, String message) {
        // when
        ResponseEntity<Map<String, String>> response = searchExpectingError(queryString);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.getBody()).containsExactly(Map.entry(parameter, message));
    }

    @Test
    void shouldGet400ForQueryLongerThan100CharactersAfterTrimming() {
        // given
        String longest = "a".repeat(100);

        // when
        ResponseEntity<Map<String, String>> tooLong = searchExpectingError("?q=" + longest + "b");
        var paddedLongest = search("?q= " + longest + " ");

        // then
        assertThat(tooLong.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(tooLong.getBody()).containsExactly(Map.entry("q", "must be at most 100 characters"));
        assertThat(paddedLongest.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void shouldGet400ForMoreThan20Categories() {
        // given
        String categories = String.join(",", IntStream.rangeClosed(1, 21).mapToObj(i -> "c" + i).toList());

        // when
        ResponseEntity<Map<String, String>> response = searchExpectingError("?category=" + categories);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsExactly(Map.entry("category", "must list at most 20 categories"));
    }

    @Test
    void shouldReportEveryInvalidParameterAtOnce() {
        // when
        ResponseEntity<Map<String, String>> response = searchExpectingError("?sort=bad&size=0&page=-2&minRating=9");

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsOnlyKeys("sort", "size", "page", "minRating");
    }

    @Test
    void shouldKeepProductListAsBareArray() {
        // given
        product("Lamp", "x", "Misc", "1.00", 1);

        // when
        ResponseEntity<String> response = executeGet(PRODUCTS_ENDPOINT, getHeadersWith(clientToken), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).startsWith("[").contains("\"name\":\"Lamp\"").doesNotContain("averageRating");
    }
}
