# Phase 2 Notes – DTO Updates & Tool Streaming

- `ChatRequestDto` now carries `tools` so clients can advertise JSON-schema definitions directly. Streaming stays opt-in (default true) because Ollama’s latest parser supports tool calls mid-stream (`streamingtools.md` reference).
- `ChatMessageDto` learned the `tool` role, `tool_name`, and `tool_calls`, enabling us to represent every message emitted by the model or by backend tools. Validation ensures a tool response always includes the name that Ollama expects back.
- Added DTOs to mirror Ollama’s function schema (`OllamaToolDefinitionDto`, `OllamaToolFunctionDto`, `OllamaToolParametersDto`, etc.) and tool-call payloads (`ToolCallDto`, `ToolCallFunctionDto`). Future tools can re-use them without bespoke JSON building.
- `OllamaService.chat` now logs tool-call chunks while still forwarding SSE events unchanged, so workshop participants can see exactly when the model requests a function.
- Testing guidance: unit tests should cover message validation (tool role + tool_name), serialization of tool schema DTOs, and service behavior when tool calls arrive mid-stream. WireMock integration tests can stub `/api/chat` to emit a tool call chunk and verify that `ChatMessageDto.toolCalls` is populated.
