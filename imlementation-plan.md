# Function Calling Implementation Plan

This backend already streams plain chat completions from Ollama through `/api/ollama/chat`. To showcase function calling during workshops we will extend the chat pipeline so Ollama can request structured data from the backend, have the backend execute a real Spring service, and send the result back to the model before the final assistant message is emitted. The following phases map out the necessary work.

## Phase 1 – Requirements Alignment & Initial Function
- **Baseline audit** – Review the current `OllamaController`/`OllamaService` SSE flow, the `ChatRequestDto` contract, and the `ProductService` APIs so that we know what can be reused. Confirm Ollama models that support `tools` (per `ollamaapi.md`) are already downloaded in the Docker stack.
- **Pick a demonstrable function** – Start with a single function named `get_product_snapshot`. It takes a `productId` (number) or `name` (string) and returns `id`, `name`, `price`, `category`, `stockQuantity`, and `description`. This maps cleanly to `ProductService` and showcases how the LLM can fetch live catalog data.
- **Document schema** – Capture the exact tool definition that will be sent to Ollama:
  ```json
  {
    "type": "function",
    "function": {
      "name": "get_product_snapshot",
      "description": "Return basic catalog metadata for a product so you can answer shopper questions.",
      "parameters": {
        "type": "object",
        "properties": {
          "productId": { "type": "integer", "description": "Numeric id as shown in the catalog" },
          "name": { "type": "string", "description": "Full product name when the id is unknown" }
        },
        "oneOf": [
          { "required": ["productId"] },
          { "required": ["name"] }
        ]
      }
    }
  }
  ```
- **Decide user-facing story** – Update the README (later) to clarify that function calls are opt-in and currently expose only this lookup function.

## Phase 2 – Data Contracts & WebClient Capabilities
- **DTO updates** – Extend `ChatRequestDto` to accept a list of tool definitions and allow the caller to opt into non-streaming mode (`stream=false`) when function orchestration is needed. `ChatMessageDto` must permit the `tool` role and carry `tool_name` so we can inject tool responses per Ollama’s expectations. `ChatResponseDto` needs a `tool_calls` collection.
- **Tool modeling** – Introduce dedicated DTOs for tool definitions (`ToolDefinitionDto`, `FunctionDescriptorDto`, `JsonSchemaPropertyDto`) and tool-call payloads (`ToolCallDto`, `ToolCallFunctionDto`). Keep them flexible enough for more functions later, but start with just `type:function`.
- **Response parsing** – Update `OllamaService.chat(...)` so it can deserialize responses that contain `message.tool_calls`. When a response chunk contains tool calls rather than assistant text, surface that signal to higher-level orchestration logic.
- **Streaming strategy** – For tool-enabled requests default `stream` to `false`. (Ollama streams tool-call deltas, but orchestrating SSE plus synchronous backend calls adds complexity. We can reintroduce streaming later.) Add validation that rejects tool lists when streaming is requested.
- **Configuration** – Ensure the existing `WebClient` bean can be reused; only adjust timeout/logging if the multi-round trip takes longer than 5 minutes (currently already configured for 5 minute read/write).

## Phase 3 – Function Execution & Controller Workflow
- **Function registry** – Create a small abstraction (`FunctionCallHandler` interface) plus a registry that maps the `name` from the tool definition to a Spring bean capable of handling it. Register `ProductSnapshotFunctionHandler` that depends on `ProductService` and returns a serializable POJO with the required fields.
- **Execution pipeline** – Add a `FunctionCallingService` that orchestrates the loop:
  1. Send the user’s messages + tool definitions (no streaming) to `/api/chat`.
  2. If the response only contains assistant text, return it.
  3. If `tool_calls` are present, parse each call, execute the matching handler, and append a new `ChatMessageDto` with `role="tool"`, `tool_name`, and the serialized JSON result.
  4. Re-send the expanded message list to `/api/chat` to let the model read the tool output. Repeat if the model chains multiple functions, but enforce a sane max-depth (e.g., 3 loops) to avoid runaway calls.
  5. Stream the final assistant response to the client (either as a single chunk or by wrapping it in `ServerSentEvent` once the final response is available).
- **Controller changes** – Add a dedicated endpoint such as `POST /api/ollama/chat/tools` that accepts the new request DTO. Keep the existing `/api/ollama/chat` untouched for plain streaming chats so current clients keep working. Document that callers must include the historical messages exactly like the original endpoint.
- **Error handling** – Surface `400`s when the model asks for an unknown function or provides invalid JSON arguments. When `ProductService` throws `ProductNotFoundException`, convert it into a tool-error payload (e.g., `{"error":"Product not found"}`) so the model can gracefully answer the user.
- **Security & auditing** – Reuse the same `@SecurityRequirement` as the existing controller. Log each tool invocation (inputs + success/failure) via SLF4J so workshop attendees can see the trace in Docker logs alongside the SSE stream.

## Phase 4 – Testing, Documentation & Observability
- **Unit coverage** – Add tests for the new DTO validation, the registry routing logic, and the `ProductSnapshotFunctionHandler`. Mock the `WebClient` (or use WireMock) to simulate Ollama responses with and without `tool_calls`. Ensure failure cases (unknown tool, invalid JSON, product missing) are covered.
- **End-to-end slice** – Create an integration test that boots the context, stubs Ollama via WireMock, triggers `/api/ollama/chat/tools`, and asserts that the backend executes the product function and returns the assistant’s final reply. This matches the existing testing style outlined in `README.md`.
- **Documentation** – Update `README.md` with (1) the new endpoint, (2) example curl payload showing the tool definition, (3) a sample response. Mention how Docker users can observe the logs to see the function invocation. If workshops need a slide-friendly summary, link to this plan.
- **Metrics/monitoring** – Optionally emit Micrometer counters for “function-call success/failure” and “total execution time” so Prometheus/Grafana dashboards can visualize tool usage during workshops. This is not mandatory for the first iteration but should be noted for quick follow-up.

Following these phases will add a concrete, minimal function-calling scenario to the backend while keeping the door open for additional Ollama tools later on (e.g., order status lookups, cart totals, etc.).
