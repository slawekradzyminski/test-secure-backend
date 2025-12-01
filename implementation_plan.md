# Frontend Function Calling Implementation Plan

The backend now supports `/api/ollama/chat/tools`, streaming tool-call loops plus tool output chunks. The Vite/React frontend currently exposes `/llm/chat` and `/llm/generate` using `useOllamaChat` and `useOllamaGenerate`; neither knows about tool definitions or the new endpoint. This plan describes how to expose the function-calling experience in the UI so workshop participants can watch the `get_product_snapshot` flow end to end. Work is divided into four phases so we can land the minimal loop first and then layer on UX polish and automated coverage.

## Phase 1 – Discovery & UX Decisions
1. **Inventory current chat UX** – Review `useOllamaChat`, `OllamaChatPage`, and `processSSEResponse` to understand how SSE chunks are handled, how the message array is rendered, and how the system prompt is injected (currently `/users/system-prompt` on mount). Decide whether to extend the existing hook or introduce a sibling hook dedicated to tool-chat mode.
2. **Tool schema definition** – Mirror the backend’s `get_product_snapshot` descriptor (name, description, `oneOf` requirement) in a TypeScript constant so both the hook and forms can reference the same schema. Capture it in `src/lib/ollamaTools.ts` (new module) along with helper methods to generate the request `tools` array and the initial “system” message text.
3. **Interaction design** – Define how participants opt into tool calling. Options:
   - a toggle/switch within the existing chat view (“Stream live catalog data”).
   - or a dedicated tab/route (e.g., `/llm/tools`), reusing existing layout but with tool-specific copy.  
   Document the chosen UX, including how we expose the backend’s system prompt (read-only banner vs editable field) so facilitators control the instructions that nudge the model to call the function.
4. **Streaming presentation** – Decide how `role:"tool"` chunks are visualized. Proposal: render them as labeled “Function Output” cards with Syntax-highlighted JSON so attendees can see the live snapshot before the assistant responds. Capture any accessibility rules (monospace font, copy-to-clipboard button) and note that ordinary assistant/user messages should continue to look the same.

## Phase 2 – API & State Management
1. **Type updates** – Extend `src/types/ollama.ts` so the `ChatMessageDto` union includes optional `tool_calls`, `tool_name`, and `thinking` (already present) as well as a discriminated union for tool messages. Add `OllamaToolDefinition`, `OllamaToolCall`, and `OllamaToolFunction` interfaces mirroring the backend DTOs.  
2. **API client** – Add `ollama.chatWithTools(body: ChatRequestDto): Promise<Response>` to `src/lib/api.ts`. This should POST to `/api/ollama/chat/tools` with `Accept: text/event-stream`, reuse the `fetch` + `AbortController` logic already used in `ollama.chat`, and pipe through `handleApiErrors`. Export the tool schema constant from Phase 1 so hooks don’t duplicate JSON.
3. **Hook architecture** – Introduce `useOllamaToolChat` (either a wrapper around `useOllamaChat` or a standalone hook) that:
   - Initializes `messages` with the fetched system prompt plus any facilitator edits.
   - Injects a `toolHistory` array that stores `role:"tool"` events separately for display.
   - Calls `ollama.chatWithTools` with `messages`, `model`, `options`, `think`, and the shared tool definition.  
   - Uses `processSSEResponse` to stream messages. When it sees a tool-call chunk (`message.tool_calls?.length`), append a placeholder “assistant” message so the history reflects that the model is requesting data. When a `role:"tool"` chunk arrives, push it into `toolHistory` and into the main `messages` array so subsequent assistant chunks display under the existing UI.  
   - Handles `done` events the same way as the legacy chat hook (flip `isChatting`, emit toasts on errors, etc.), but also resets temporary state if the backend sends an error chunk (e.g., tool returns `{ "error": "Product not found" }`).
4. **Global state & prompt editing** – The backend allows admins to update their system prompt (`/users/system-prompt`). For workshops we want to surface this ability in the UI (likely via a drawer/modal). In this phase create the API plumbing (GET/PUT already exist in `systemPrompt` client) and store the fetched prompt in context so both the legacy chat and the tool chat share the same source of truth.

## Phase 3 – UI Implementation
1. **Pages & routing** – Depending on Phase 1’s decision:
   - If we reuse `/llm/chat`, add a toggle to switch “Plain model” vs “Product-aware model.” When toggled on, render the tool-specific controls.  
   - If we add a new page (e.g., `/llm/tools`), create a `ToolChatPage` that composes the new hook, stores chat history, and explains the feature with onboarding copy (what tool is available, what questions to ask).
2. **Message rendering** – Update the chat transcript component to handle four cases:
   - user messages (no change),
   - assistant messages (no change),
   - tool call announcements (maybe show “Calling get_product_snapshot with name=iPhone 13 Pro…”),
   - tool outputs (render JSON card).  
   Use icons/colors to differentiate each type and consider a collapsible JSON viewer for long content.
3. **Form enhancements** – Provide affordances for specifying a product before asking a question. Ideas: a “Suggested prompts” list (“What’s the price of the iPhone 13 Pro?”) or a dedicated input for product name that pre-fills the user message. Not required for phase completion but document whichever subset we commit to.
4. **Loading & error states** – Show skeleton/loading indicators while fetching the system prompt, display inline toasts when the backend emits `{ "error": ... }`, and disable the send button until both the prompt and the previous response are complete. Ensure the SSE connection is aborted when the component unmounts (cleanup in the hook).

## Phase 4 – Testing, Docs & Demos
1. **Unit tests** – Extend `useOllamaChat.test.tsx` (or create `useOllamaToolChat.test.tsx`) to cover:
   - building the correct payload (model, think flag, temperature, tools array),
   - handling tool-call chunks followed by tool output,
   - surfacing backend errors (HTTP + SSE).  
   Mock `ollama.chatWithTools` to emit custom SSE fixtures using the `createSseResponse` helper.
2. **Component tests** – Update `OllamaChatPage.test.tsx` (and add new tests if we create `ToolChatPage`) to assert that toggling the new mode renders tool results, that placeholders disappear when done, and that validation toasts show up when fields are empty.
3. **Playwright coverage** – Add a new `e2e/tests/llm.tools.spec.ts` mocking the `/api/ollama/chat/tools` endpoint. Simulate:
   - successful flow with tool chunk followed by assistant response,
   - backend error chunk (e.g., product not found),
   - streaming with thinking content.  
   Assert that the UI displays the tool JSON card before the assistant summary, mirroring the WireMock integration test on the backend.
4. **Documentation/demo scripts** – Update `README.md` (or create a docs page) with a section on “Tool-enabled LLM demo” describing:
   - how to launch Docker services,
   - which models are supported (qwen3:8b),
   - screenshots or GIF showing the tool output bubble.  
   Include a facilitator checklist (refresh system prompt, ensure inventory contains iPhone 13 Pro, recommended prompts). Optionally add a short Loom/video reference for workshop prep.

Following these phases will add a polished, workshop-ready UI that showcases backend function calling, keeps the UX approachable for participants, and provides enough automated coverage for confident demos.
