# Persistent Conversation History – Implementation Plan

## Phase 1 – Backend Domain & API Foundations
1. **Modeling**
   - Introduce `ConversationEntity` (id, user, type, title, model, temperature, think, systemPromptSnapshot, timestamps, archived flag).
   - Add `ConversationMessageEntity` storing ordered `ChatMessageDto` payloads (role, content, thinking, tool info/JSON).
   - Create JPA repositories plus basic query coverage tests for ordering and cascade behavior.
2. **Service layer**
   - Build `ConversationHistoryService` to own conversation creation, ownership validation, message appends, snapshotting user prompts, and auto-titling.
   - Ensure tool calls/tool outputs serialize correctly (Jackson conversions).
3. **REST surface**
   - Add `ConversationController` providing CRUD:
     - `GET /api/ollama/conversations?type=` – list summaries.
     - `POST /api/ollama/conversations` – create new thread (plain or tool).
     - `GET /api/ollama/conversations/{id}` – hydrate transcript + metadata.
     - `PATCH/DELETE` – rename, archive, or remove.
   - Keep legacy stateless `/api/ollama/chat` endpoints for backward compatibility but mark them legacy in Swagger.
4. **Docs/tests**
   - Extend Spring MVC and repository tests for new endpoints.
   - Document schema/API changes in README and OpenAPI annotations.

## Phase 2 – Streaming Integration & Tool Support
1. **Controller wiring**
   - Add conversation-aware streaming endpoints:
     - `POST /api/ollama/conversations/{id}/chat`
     - `POST /api/ollama/conversations/{id}/chat/tools`
     - Payload contains only the latest user message plus optional overrides.
   - Reuse `PromptInjector` with the stored system prompt snapshot; forbid cross-user access.
2. **Flux interception**
   - Wrap `OllamaService`/`OllamaFunctionCallingService` responses to accumulate assistant chunks, persist the final assistant message, and update timestamps once `done` arrives.
   - For tool chat, persist:
     - User prompt.
     - Assistant tool-call requests (with JSON blob for `tool_calls`).
     - Tool outputs streamed by `OllamaFunctionCallingService`.
3. **Iteration limits & error handling**
   - Ensure tool iterations still cap at `MAX_TOOL_CALL_ITERATIONS`, logging conversation context.
   - On SSE errors, store a failed assistant message so the UI can display error state after reload.
4. **Tests**
   - Extend existing controller/service tests to verify persistence after streamed responses.
   - Mock Ollama WebClient to emit mixed assistant/tool chunks to guarantee serialization parity.

## Phase 3 – Frontend State & API Client
1. **Types & API layer**
   - Add `ConversationSummary`, `ConversationDetail`, `ConversationMessage` interfaces in `src/types`.
   - Extend `src/lib/api.ts` with a `conversations` client (list, create, get, rename, delete/archive, sendMessage) plus streaming helpers hitting the new backend URLs.
2. **Hooks**
   - Create `useConversations` (or context) to keep conversation lists per mode synchronized; expose actions for new chat, select, rename, delete.
   - Refactor `useOllamaChat`/`useOllamaToolChat` to:
     - Hydrate from backend transcripts.
     - Send only the latest user input via conversation streaming endpoints.
     - Append optimistic user messages while streaming assistant chunks.
     - Track per-conversation settings (model, temperature, think).
3. **Routing**
   - Update `AppRoutes` and LLM pages so URLs include optional `conversationId` (`/llm/chat/:conversationId?`, `/llm/tools/:conversationId?`).
   - On first load, auto-create a conversation if none exist, then navigate to its URL.
4. **Testing**
   - Update hook/unit tests with mocked conversation APIs.
   - Adjust Playwright specs to ensure transcripts survive reloads and conversation actions work.

## Phase 4 – Frontend UX Enhancements
1. **Sidebar & navigation**
   - Add a reusable `ConversationSidebar` showing recent chats with actions (new, rename inline, delete/archive) and active highlighting.
   - Support empty states (“Start a new chat”) and show skeleton loaders while fetching.
2. **Main workspace updates**
   - Ensure `ChatTranscript` renders persisted tool outputs and thinking traces after reload.
   - Surface conversation metadata (model, last updated) near the transcript header.
   - Offer quick rename suggestions after assistant replies (e.g., use first user utterance).
3. **Settings persistence**
   - When the user changes sliders (model/temperature/think), patch the conversation record so future sessions reuse the same defaults.
   - Display the stored `systemPromptSnapshot` in the prompt accordion so users know which instructions were active when the conversation started.
4. **Error/edge UX**
   - Handle streaming failures by showing inline error toasts and leaving the transcript intact.
   - Prevent send while a stream is active, mirroring existing behavior but scoped per conversation.
5. **QA**
   - Re-run Playwright suites for chat + tools and add test cases covering sidebar interactions and persistence after navigation/reload.
