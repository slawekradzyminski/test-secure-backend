# Phase 1 – Baseline & Function Definition

## Current Ollama Integration Snapshot
- `src/main/java/com/awesome/testing/controller/OllamaController.java:21` exposes `/api/ollama/generate` and `/api/ollama/chat` as SSE streams. Both delegate to `OllamaService` and currently support only plain text or thinking-mode streaming.
- `src/main/java/com/awesome/testing/service/ollama/OllamaService.java:14` wraps an `ollamaWebClient` POST to `/api/generate` and `/api/chat`. Responses are streamed chunk-by-chunk and simply logged by `OllamaRequestHandler`; there is no orchestration loop to inspect tool calls or send follow-up messages.
- DTO constraints:
  - `ChatRequestDto` (`src/main/java/com/awesome/testing/dto/ollama/ChatRequestDto.java:20`) only allows `system|user|assistant` roles and lacks a `tools` array or `tool_choice`. Streaming is forced on and keep-alive defaults to 10000 minutes.
  - `ChatMessageDto` forbids the `tool` role and asserts that either `content` or `thinking` is set (`src/main/java/com/awesome/testing/dto/ollama/ChatMessageDto.java:12`).
  - `ChatResponseDto` models the Ollama streaming payload but omits an explicit `tool_calls` collection.
- `WebClient` setup in `src/main/java/com/awesome/testing/config/OllamaConfig.java:17` already configures 5-minute read/write timeouts, which is sufficient for a multi-step function-call exchange in Docker-based workshops.
- Product data is already exposed through `ProductService` (`src/main/java/com/awesome/testing/service/ProductService.java:20`) with methods to fetch by id and map to `ProductDto`, providing a natural backend capability for our first tool.

## Initial Function: `get_product_snapshot`
- **Purpose** – Allow the model to answer catalog questions with live backend data (price, stock, category, etc.) instead of hallucinating. Perfect for training demos because it shows a tangible round-trip from LLM ➜ backend ➜ LLM.
- **Source of truth** – Uses `ProductService#getProductById` (and later an optional search by name). The DTO already returns `id`, `name`, `description`, `price`, `stockQuantity`, `category`, and `imageUrl`, so no schema redesign is required.
- **Invocation guarantees** – Runs under the same authenticated context as the chat endpoint; leverages existing transaction boundaries inside `ProductService`.

## Tool Definition to Send to Ollama
```json
{
  "type": "function",
  "function": {
    "name": "get_product_snapshot",
    "description": "Return catalog metadata for a product so you can answer shopper questions accurately.",
    "parameters": {
      "type": "object",
      "properties": {
        "productId": {
          "type": "integer",
          "description": "Numeric product id from the catalog."
        },
        "name": {
          "type": "string",
          "description": "Exact product name when the id is unknown."
        }
      },
      "oneOf": [
        { "required": ["productId"] },
        { "required": ["name"] }
      ]
    }
  }
}
```
- **Return payload** – JSON object mirroring `ProductDto`: `{ "id": 1, "name": "...", "description": "...", "price": 12.99, "stockQuantity": 42, "category": "ELECTRONICS", "imageUrl": "..." }`. Optional error payload `{ "error": "Product not found" }` when lookup fails.

## Workshop Storyboard
1. Shopper asks, “How much does the Retro Console cost and is it in stock?”
2. Frontend sends the running chat plus the `get_product_snapshot` tool definition to `/api/ollama/chat/tools`.
3. The LLM triggers a `tool_call` with `{ "productId": 5 }`.
4. Backend executes `ProductService.getProductById(5)`, serializes the snapshot, and appends a `role: "tool"` message so Ollama can craft the final assistant reply referencing real numbers.
5. Logs show both the tool invocation and the final streamed response, giving trainees visibility into each step during Docker workshops.

## Phase-1 Deliverables
- Baseline analysis (above) confirms that DTO and service layers need extension before tool calls can be processed.
- Function spec and schema are locked, enabling Phase 2 to focus on DTO updates and WebClient handling without re-litigating requirements.
- README updates will follow once the endpoint is implemented so documentation stays in sync with working code.
