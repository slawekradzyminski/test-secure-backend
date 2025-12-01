package com.awesome.testing.endpoints.ollama;

import lombok.experimental.UtilityClass;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

@UtilityClass
public class OllamaMock {

    // api/generate
    public static void stubSuccessfulGeneration() {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:24Z","response":"Hello","done":false,"context":null,"total_duration":null}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:25Z","response":"world","done":false,"context":null,"total_duration":null}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:25Z","response":"my friend","done":true,"context":[2456, 4567],"total_duration":200000000}
                                """)
                        .withChunkedDribbleDelay(2, 10)));
    }

    public static void stubSuccessfulGenerationWithThinking() {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:24Z","response":"","thinking":"Let me think...","done":false,"context":null,"total_duration":null}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:25Z","response":"","thinking":" about this question.","done":false,"context":null,"total_duration":null}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:26Z","response":"Hello","thinking":"","done":false,"context":null,"total_duration":null}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:27Z","response":" world!","thinking":"","done":true,"context":[2456, 4567],"total_duration":300000000}
                                """)
                        .withChunkedDribbleDelay(2, 10)));
    }

    public static void stubModelNotFound() {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": "model 'qwen3:0.6b' not found"}
                                """)));
    }

    public static void stubServerError() {
        stubFor(post(urlEqualTo("/api/generate"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal server error\",\"message\":\"Failed to process request\"}")
                ));
    }

    // api/chat
    public static void stubSuccessfulChat() {
        stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:24Z","message":{"role":"assistant","content":"Hi"},"done":false}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:25Z","message":{"role":"assistant","content":"there"},"done":false}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:25Z","message":{"role":"assistant","content":"friend!"},"done":true}
                                """)
                        .withChunkedDribbleDelay(2, 10)));
    }

    public static void stubSuccessfulChatWithThinking() {
        stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:24Z","message":{"role":"assistant","content":"","thinking":"I need to think"},"done":false}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:25Z","message":{"role":"assistant","content":"","thinking":" about this..."},"done":false}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:26Z","message":{"role":"assistant","content":"Hi there!","thinking":""},"done":false}
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:27Z","message":{"role":"assistant","content":" How can I help?","thinking":""},"done":true}
                                """)
                        .withChunkedDribbleDelay(2, 10)));
    }

    public static void stubModelNotFoundForChat() {
        stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"error": "model 'qwen3:0.6b' not found"}
                                """)));
    }

    public static void stubServerErrorForChat() {
        stubFor(post(urlEqualTo("/api/chat"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal server error\",\"message\":\"Failed to process request\"}")
                ));
    }

    public static void stubToolCallingChatScenario() {
        stubFor(post(urlEqualTo("/api/chat"))
                .inScenario("tool-chat")
                .whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:24Z","message":{"role":"assistant","content":"","tool_calls":[{"function":{"name":"get_product_snapshot","arguments":{"name":"iPhone 13 Pro"}}}]},"done":true}
                                """)
                        .withFixedDelay(5))
                .willSetStateTo("tool-called"));

        stubFor(post(urlEqualTo("/api/chat"))
                .inScenario("tool-chat")
                .whenScenarioStateIs("tool-called")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"model":"qwen3:0.6b","created_at":"2025-02-21T14:28:30Z","message":{"role":"assistant","content":"The iPhone 13 Pro currently costs $999.99 with 50 units in stock."},"done":true}
                                """)
                        .withFixedDelay(5))
                .willSetStateTo("completed"));
    }
}
