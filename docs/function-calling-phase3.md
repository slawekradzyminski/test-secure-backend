# Phase 3 – Function Execution Workflow

- **Loop orchestration** – `OllamaFunctionCallingService` wraps the chat workflow in a controlled loop (max three tool rounds). For every iteration it:
  1. Replays the running history against `/api/chat` (respecting `stream` so chunks are forwarded to the client immediately).
  2. Captures the terminal assistant message to see whether the model emitted `tool_calls`.
  3. Stops when no tool calls remain; otherwise executes each requested function via the registry and appends `role="tool"` messages before the next loop.
- **Registry & handlers** – `OllamaToolRegistry` maps function names to Spring beans that implement `FunctionCallHandler`. The only handler today is `ProductSnapshotFunctionHandler`, but new beans can register additional capabilities simply by returning a different `getName()`.
- **Product snapshot** – The handler accepts either `productId` or `name`, reuses `ProductService` getters, and serializes a `ProductDto` into JSON. Errors (missing/invalid arguments, not found, server exceptions) are surfaced as `{ "error": "<message>" }` so the model can gracefully inform the user.
- **Controller exposure** – `/api/ollama/chat/tools` lives alongside the original chat endpoints in `OllamaController` and is protected by the same JWT requirement. Because responses are still streamed as SSE, the frontend can log each stage of the loop without waiting for the final answer.
