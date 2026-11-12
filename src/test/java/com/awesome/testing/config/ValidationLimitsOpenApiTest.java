package com.awesome.testing.config;

import com.awesome.testing.HttpHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/** The published OpenAPI states the validation limits the API enforces (catalog T-002 to T-015). */
@TestPropertySource(properties = {
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=false"
})
class ValidationLimitsOpenApiTest extends HttpHelper {

    private JsonNode schemas;
    private JsonNode paths;

    @BeforeEach
    void loadGeneratedContract() throws Exception {
        ResponseEntity<String> response = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode spec = new ObjectMapper().readTree(response.getBody());
        schemas = spec.path("components").path("schemas");
        paths = spec.path("paths");
    }

    @Test
    void signupPublishesTheUsernameCharacterSetAndTheLengthLimits() {
        JsonNode signup = schemas.path("UserRegisterDto").path("properties");

        assertLength(signup.path("username"), 4, 255);
        assertThat(signup.path("username").path("pattern").asText()).isEqualTo("^[A-Za-z0-9._-]*$");
        assertLength(signup.path("firstName"), 4, 255);
        assertLength(signup.path("lastName"), 4, 255);
        assertLength(signup.path("password"), 8, 72);
        assertThat(signup.path("password").path("description").asText()).contains("72 bytes");
        assertThat(signup.path("email").path("minLength").asInt()).isEqualTo(1);
    }

    @Test
    void passwordResetPublishesTheByteLimit() {
        assertLength(schemas.path("ResetPasswordRequestDto").path("properties").path("newPassword"), 8, 72);
    }

    @Test
    void signinMarksBothCredentialsRequired() {
        JsonNode login = schemas.path("LoginDto");

        assertThat(login.path("required").toString()).contains("\"username\"", "\"password\"");
        assertLength(login.path("properties").path("username"), 4, 255);
        assertLength(login.path("properties").path("password"), 4, 255);
    }

    @Test
    void productCategoryAndImageUrlPublishTheColumnLimit() {
        for (String dto : new String[] {"ProductCreateDto", "ProductUpdateDto"}) {
            JsonNode properties = schemas.path(dto).path("properties");
            assertThat(properties.path("category").path("maxLength").asInt()).as(dto).isEqualTo(255);
            assertThat(properties.path("imageUrl").path("maxLength").asInt()).as(dto).isEqualTo(255);
        }
    }

    @Test
    void addressFieldsPublishTheColumnLimit() {
        JsonNode address = schemas.path("AddressDto").path("properties");

        for (String field : new String[] {"street", "city", "state", "country"}) {
            assertThat(address.path(field).path("maxLength").asInt()).as(field).isEqualTo(255);
        }
    }

    @Test
    void theIdentityIssuesEndpointIsDocumentedForAdministrators() {
        JsonNode responses = paths.path("/api/v1/admin/users/identity-issues").path("get").path("responses");

        assertThat(responses.path("200").path("content").path("application/json").path("schema").path("items")
                .path("$ref").asText()).endsWith("/UserIdentityIssueDto");
        assertThat(responses.has("401")).isTrue();
        assertThat(responses.has("403")).isTrue();
    }

    private static void assertLength(JsonNode property, int min, int max) {
        assertThat(property.path("minLength").asInt()).as(property.toString()).isEqualTo(min);
        assertThat(property.path("maxLength").asInt()).as(property.toString()).isEqualTo(max);
    }
}
