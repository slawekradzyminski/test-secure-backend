package com.awesome.testing.endpoints.embeddings;

import com.awesome.testing.DomainHelper;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpStatus;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@AutoConfigureWireMock(port = 0)
public abstract class AbstractEmbeddingsTest extends DomainHelper {

    protected static final String API_EMBEDDINGS = "/api/embeddings";
    protected static final String API_EMBEDDINGS_ENDPOINT = API_EMBEDDINGS + "/embeddings";
    protected static final String API_ATTENTION_ENDPOINT = API_EMBEDDINGS + "/attention";
    protected static final String API_REDUCE_ENDPOINT = API_EMBEDDINGS + "/reduce";

    protected void stubEmbeddingsSuccess() {
        stubFor(post(urlEqualTo("/embeddings"))
                .withHeader("Accept", equalTo("application/json"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                            {
                                "tokens": ["Hello", "Ġworld"],
                                "embeddings": [
                                    [-9.357813E-6, -0.1402097, -0.20845123, -0.028111305],
                                    [-0.16632557, 0.21910384, 0.04447212, 0.02384877]
                                ],
                                "model_name": "gpt2"
                            }
                            """)));
    }

    protected void stubAttentionSuccess() {
        stubFor(post(urlEqualTo("/attention"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                            {
                                "tokens": ["Hello", "Ġworld"],
                                "attention": [
                                    [
                                        [[1.0, 0.0], [0.95400715, 0.04599281]],
                                        [[1.0, 0.0], [0.0022541583, 0.9977458]]
                                    ],
                                    [
                                        [[1.0, 0.0], [0.9621648, 0.03783513]],
                                        [[1.0, 0.0], [0.97924465, 0.020755326]]
                                    ]
                                ],
                                "model_name": "gpt2"
                            }
                            """)));
    }

    protected void stubReduceSuccess() {
        stubFor(post(urlEqualTo("/reduce"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                                {
                                    "tokens": ["Hello", "Ġworld"],
                                    "reduced_embeddings": [
                                        [-1.0, -1.0],
                                        [1.000000238418579, -1.0]
                                    ],
                                    "model_name": "gpt2"
                                }
                                """)));
    }

    protected void stubEmbeddingsServerError() {
        stubFor(post(urlEqualTo("/embeddings"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                                {
                                    "error": "Internal server error",
                                    "message": "Failed to process embeddings request"
                                }
                                """)));
    }

    protected void stubAttentionServerError() {
        stubFor(post(urlEqualTo("/attention"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                                {
                                    "error": "Internal server error",
                                    "message": "Failed to process attention request"
                                }
                                """)));
    }

    protected void stubReduceServerError() {
        stubFor(post(urlEqualTo("/reduce"))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader("Content-Type", "application/json; charset=UTF-8")
                        .withBody("""
                                {
                                    "error": "Internal server error",
                                    "message": "Failed to process reduce request"
                                }
                                """)));
    }
}
