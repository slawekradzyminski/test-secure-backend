# Phase 4 – Testing & Validation Notes

- **Unit tests** – `ProductSnapshotFunctionHandlerTest` verifies the happy paths (lookup by id or name) and error handling (missing product, invalid ids). `OllamaToolRegistryTest` confirms that tool calls are routed to the correct handler and that unsupported tools raise an error immediately. `ProductServiceTest` now covers the new `getProductByName` method.
- **Integration tests (next step)** – Use WireMock to stub `/api/chat` responses that emit a `tool_calls` chunk followed by a final assistant message. Hitting `/api/ollama/chat/tools` in that scenario should stream: (1) the assistant chunk with the tool call, (2) a synthetic tool message from the backend, and (3) the final assistant reply after the tool output is injected into the history. Capture these via `StepVerifier` or WebTestClient.
- **Manual verification** – Run the Docker stack, open two terminals:
  1. Terminal A: `docker compose up backend ollama` to watch backend logs. You should see lines such as `tool_call function=get_product_snapshot arguments={productId=5}` followed by the tool execution log.
  2. Terminal B: `curl -N http://localhost:4001/api/ollama/chat/tools -H "Authorization: Bearer <token>" -d '<payload>'` to observe the SSE stream. Confirm that the tool payload contains the actual product JSON before the final assistant message arrives.
- **Future hooks** – When adding more handlers, include dedicated unit tests plus registry coverage, and extend the WireMock scenario with multiple sequential calls to ensure the loop depth guard behaves as expected.
