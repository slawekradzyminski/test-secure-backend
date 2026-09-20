package com.awesome.testing.graphql;

import com.awesome.testing.DomainHelper;
import graphql.introspection.IntrospectionQuery;
import com.awesome.testing.dto.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.graphql.execution.GraphQlSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static com.awesome.testing.factory.UserFactory.getRandomUserWithRoles;
import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:graphqldefault;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "springdoc.api-docs.enabled=true",
        "springdoc.swagger-ui.enabled=true"
})
class GraphQlDefaultAvailabilityTest extends DomainHelper {
    @Autowired
    private ApplicationContext context;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void graphqlIsAvailableWithoutExplicitProfileAlongsideRestAndSwagger() {
        // given
        String token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        var graphql = executePost("/api/v1/graphql", Map.of("query", "{cart {totalItems}}"),
                getHeadersWith(token), String.class);
        var rest = executeGet("/api/v1/products", getHeadersWith(token), String.class);
        var openApi = executeGet("/v3/api-docs", getJsonOnlyHeaders(), String.class);
        var swagger = executeGet("/v3/api-docs/swagger-config", getJsonOnlyHeaders(), String.class);

        // then
        assertThat(context.getBeansOfType(GraphQlSource.class)).hasSize(1);
        assertThat(context.getBeansOfType(org.springframework.grpc.server.lifecycle.GrpcServerLifecycle.class)).isEmpty();
        assertThat(graphql.getStatusCode()).isEqualTo(HttpStatus.OK);
        var result = mapper.readTree(graphql.getBody());
        assertThat(result.has("errors")).isFalse();
        assertThat(result.at("/data/cart").isObject()).isTrue();
        assertThat(result.at("/data/cart/totalItems").asInt()).isZero();
        assertThat(rest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        var paths = mapper.readTree(openApi.getBody()).path("paths");
        assertThat(paths.has("/api/v1/products")).isTrue();
        assertThat(paths.has("/api/v1/graphql")).isFalse();
        assertThat(swagger.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void graphiqlEditorIsPublicButGraphqlExecutionRequiresAuthentication() {
        // given
        var headers = getJsonOnlyHeaders();

        // when
        var editor = executeGet("/api/v1/graphiql?path=/api/v1/graphql", headers, String.class);
        var query = executePost("/api/v1/graphql", Map.of("query", "{__typename}"), headers, String.class);
        var editorPost = executePost("/api/v1/graphiql", Map.of(), headers, String.class);

        // then
        assertThat(editor.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(editor.getHeaders().getContentType().toString()).startsWith("text/html");
        assertThat(editor.getBody()).contains("<title>GraphiQL</title>", "headerEditorEnabled: true");
        assertThat(query.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(editorPost.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void authenticatedUsersCanDiscoverTheSchemaInGraphiql() {
        // given
        String token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));

        // when
        var response = executePost("/api/v1/graphql", Map.of("query", IntrospectionQuery.INTROSPECTION_QUERY),
                getHeadersWith(token), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var result = mapper.readTree(response.getBody());
        assertThat(result.has("errors")).describedAs(response.getBody()).isFalse();
        assertThat(result.at("/data/__schema/queryType/name").asText()).isEqualTo("Query");
        assertThat(result.at("/data/__schema/mutationType/name").asText()).isEqualTo("Mutation");
    }


    @Test
    void schemaExplorationStillHasABoundedQueryDepth() {
        // given
        String token = getToken(getRandomUserWithRoles(List.of(Role.ROLE_CLIENT)));
        String query = "{__type(name: \"Product\") {" + "ofType {".repeat(20) + "name" + "}".repeat(22);

        // when
        var response = executePost("/api/v1/graphql", Map.of("query", query), getHeadersWith(token), String.class);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("maximum query depth exceeded");
        assertThat(mapper.readTree(response.getBody()).has("data")).isFalse();
    }

}
