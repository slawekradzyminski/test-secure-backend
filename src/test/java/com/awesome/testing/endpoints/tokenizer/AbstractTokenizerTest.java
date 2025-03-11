package com.awesome.testing.endpoints.tokenizer;

import com.awesome.testing.DomainHelper;
import org.springframework.http.HttpStatus;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public abstract class AbstractTokenizerTest extends DomainHelper {

    private WireMockServer wireMockServer;

    @BeforeEach
    public void setup() {
        wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        configureFor("localhost", 8080);
    }

    @AfterEach
    public void teardown() {
        wireMockServer.stop();
    }

    protected void stubTokenizeSuccess() {
        stubFor(post(urlEqualTo("/tokenize"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                            {
                                "tokens": ["Hello", "from", "Java", "test"],
                                "model_name": "gpt2"
                            }
                            """)));
    }

    protected void stubTokenizeServerError() {
        stubFor(post(urlEqualTo("/tokenize"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                                {
                                    "error": "Internal server error",
                                    "message": "Failed to process tokenize request"
                                }
                                """)));
    }
} 