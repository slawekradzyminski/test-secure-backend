# Frontend Function Calling – Phase 1 Notes

Date: 2025‑11‑30  
Author: Codex CLI Agent

This document captures the discovery work, design decisions, and shared artefacts required before implementing tool-calling support in the Vite/React frontend.

## 1. Baseline Audit

### Existing flow
- `/llm/chat` renders `OllamaChatPage`, which relies on `useOllamaChat`.
- `useOllamaChat` keeps the entire chat history in component state and streams updates via `processSSEResponse`.
- The hook loads the user’s system prompt from `/users/system-prompt` on mount and inserts it as the first `system` message.
- SSE chunks are applied cumulatively to a single assistant placeholder (`assistantIndex`). There is no notion of intermediate roles (e.g., `tool`), so any new message types must be stored separately.
- API client (`ollama.chat`) POSTs to `/api/ollama/chat` and returns the raw `Response` so hooks can decode SSE manually.

### Implications
1. We can reuse most of the SSE plumbing; however, we need parallel arrays for tool messages (to render them distinctly) and maybe to append new assistant placeholders when the backend resubmits messages between tool iterations.
2. System prompts are already editable via backend APIs. We should expose that UI to facilitators in the tool chat as well.
3. Error handling currently surfaces only HTTP/network failures. Tool execution failures (e.g., `{ "error": "Product not found" }`) must be detected inside the SSE loop and visualized inline.

## 2. Tool Schema Artefact

Frontend will maintain the same schema as the backend to keep requests in sync. During Phase 2 we’ll convert this JSON into a TypeScript constant exported from `src/lib/ollamaTools.ts`.

```ts
export const PRODUCT_SNAPSHOT_TOOL = {
  type: 'function',
  function: {
    name: 'get_product_snapshot',
    description: 'Return catalog metadata for a product so you can answer shopper questions accurately.',
    parameters: {
      type: 'object',
      properties: {
        productId: {
          type: 'integer',
          description: 'Numeric product id as shown in the catalog.'
        },
        name: {
          type: 'string',
          description: 'Exact product name when the id is unknown.'
        }
      },
      oneOf: [
        { required: ['productId'] },
        { required: ['name'] }
      ]
    }
  }
} as const;
```

All tool-enabled chat requests will include `tools: [PRODUCT_SNAPSHOT_TOOL]`.

## 3. Interaction Design

- **Entry point**: extend the existing `/llm/chat` view with a prominent toggle labeled *“Use live product data (calls get_product_snapshot)”*. When enabled, the chat form routes through the new hook/endpoint. Keeping everything on one page simplifies workshop instructions and preserves URL bookmarks.
- **System prompt banner**: show the current default prompt (fetched from `/users/system-prompt`) in a highlighted card with an “Edit prompt” button for admins. This ensures facilitators can paste the backend’s recommended instructions without digging into settings.
- **Suggested prompts**: beneath the input, display quick actions such as “What’s the price of the iPhone 13 Pro?” and “How many Apple Watch Series 7 units are available?” Clicking one should populate the textarea and trigger send, helping attendees reach the happy path quickly.
- **Mode indicator**: when the toggle is on, prepend each assistant reply with a subtle badge (“Catalog-powered”) to remind users they’re seeing live data.

## 4. Streaming Visualization

- **Tool call notice**: when the model emits a `tool_calls` chunk, insert a transient message reading “Calling `get_product_snapshot` with name=…”. This mirrors what the backend logs and helps participants understand why the assistant pauses before answering.
- **Tool output card**: render `role:"tool"` chunks inside a monospace panel with:
  - title “Function output – get_product_snapshot”
  - JSON pretty-print with copy-to-clipboard
  - optional collapse/expand.  
  This message should appear *before* the final assistant summary to match the backend’s SSE order.
- **Assistant narration**: existing streaming UI already concatenates content strings; leave that behavior unchanged but ensure it resumes after the tool card without overwriting it.
- **Errors**: if the tool payload contains `{ "error": "…" }`, show the card in a warning color and keep the assistant placeholder visible so the model’s follow-up (usually “I couldn’t find that product…”) still streams in.

## Outstanding Questions

1. Should we allow advanced users to supply arbitrary additional tools? For now, scope is limited to `get_product_snapshot`, but the constant is structured to make future additions easy.
2. Do we need per-message serialization in local storage? Current plan keeps chat history in memory only; acceptable for workshops but worth re-evaluating if the page reloads often.

With these decisions recorded, Phase 2 can implement the necessary types, API client additions, and hook scaffolding.
