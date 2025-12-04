package com.awesome.testing.service.ollama;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ChatRequestDto;
import com.awesome.testing.dto.ollama.ChatResponseDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.service.ollama.function.OllamaToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaFunctionCallingService {

    private static final int MAX_TOOL_CALL_ITERATIONS = 3;

    private final WebClient ollamaWebClient;
    private final OllamaToolRegistry toolRegistry;

    public Flux<ChatResponseDto> chatWithTools(ChatRequestDto request) {
        if (request.getTools() == null || request.getTools().isEmpty()) {
            return Flux.error(new IllegalArgumentException("At least one tool definition is required"));
        }
        Objects.requireNonNull(request.getMessages(), "messages must not be null");

        return Flux.create(
                sink -> runConversation(request, sink, new OllamaRequestHandler("chat-tools", request.getModel())),
                FluxSink.OverflowStrategy.BUFFER);
    }

    private void runConversation(ChatRequestDto baseRequest, FluxSink<ChatResponseDto> sink, OllamaRequestHandler ctx) {
        List<ChatMessageDto> history = new ArrayList<>(baseRequest.getMessages());
        try {
            for (int iteration = 0; iteration < MAX_TOOL_CALL_ITERATIONS; iteration++) {
                log.info("Iteration {}: forwarding {} historical messages to Ollama (tools={})",
                        iteration + 1, history.size(), baseRequest.getTools().size());
                List<ChatResponseDto> iterationChunks = sendToOllama(baseRequest, history, sink, iteration + 1, ctx);
                if (iterationChunks == null || iterationChunks.isEmpty()) {
                    log.warn("Iteration {}: Ollama returned no chunks, completing stream", iteration + 1);
                    sink.complete();
                    return;
                }

                ChatMessageDto toolRequestMessage = findLastMessageWithToolCalls(iterationChunks);
                if (toolRequestMessage != null) {
                    history.add(toolRequestMessage);
                    List<String> requestedTools = toolRequestMessage.getToolCalls().stream()
                            .map(call -> call.getFunction().getName())
                            .collect(Collectors.toList());
                    log.info("Iteration {}: Ollama requested {} tool call(s): {}",
                            iteration + 1, requestedTools.size(), requestedTools);
                    for (ToolCallDto toolCall : toolRequestMessage.getToolCalls()) {
                        String toolName = toolCall.getFunction().getName();
                        log.info("Iteration {}: Executing tool {} with args {}", iteration + 1, toolName,
                                toolCall.getFunction().getArguments());
                        ChatMessageDto toolOutput = toolRegistry.execute(toolCall);
                        log.info("Iteration {}: Tool {} responded (payload {} chars)",
                                iteration + 1, toolName,
                                toolOutput.getContent() != null ? toolOutput.getContent().length() : 0);
                        history.add(toolOutput);
                        sink.next(buildToolResponse(baseRequest.getModel(), toolOutput));
                    }
                    continue;
                }

                ChatMessageDto assistantMessage = findLastMessage(iterationChunks);
                if (assistantMessage == null) {
                    sink.complete();
                    return;
                }
                history.add(assistantMessage);
                log.info("Iteration {}: Received assistant completion ({} chars). Ending stream.",
                        iteration + 1, assistantMessage.getContent() != null ? assistantMessage.getContent().length() : 0);

                sink.complete();
                return;
            }
            log.error("Exceeded maximum tool call iterations ({}) for model {}", MAX_TOOL_CALL_ITERATIONS, baseRequest.getModel());
            sink.error(new IllegalStateException("Exceeded maximum tool call iterations"));
        } catch (Throwable t) {
            log.error("Failed to complete tool-enabled chat: {}", t.getMessage(), t);
            sink.error(t);
        }
    }

    private List<ChatResponseDto> sendToOllama(ChatRequestDto baseRequest,
                                               List<ChatMessageDto> history,
                                               FluxSink<ChatResponseDto> sink,
                                               int iteration,
                                               OllamaRequestHandler ctx) {
        ChatRequestDto iterationRequest = ChatRequestDto.builder()
                .model(baseRequest.getModel())
                .messages(List.copyOf(history))
                .options(baseRequest.getOptions())
                .tools(baseRequest.getTools())
                .stream(baseRequest.getStream())
                .keepAlive(baseRequest.getKeepAlive())
                .think(baseRequest.getThink())
                .build();

        Instant start = Instant.now();
        List<ChatResponseDto> chunks = ollamaWebClient.post()
                .uri("/api/chat")
                .bodyValue(iterationRequest)
                .retrieve()
                .bodyToFlux(ChatResponseDto.class)
                .doOnNext(resp -> {
                    logToolCalls(ctx, resp.getMessage() != null ? resp.getMessage().getToolCalls() : null);
                    handleChunk(ctx, resp);
                    sink.next(resp);
                })
                .doOnError(ctx::logError)
                .doOnComplete(ctx::logComplete)
                .collectList()
                .block();
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("Iteration {}: Ollama responded with {} chunk(s) in {} ms",
                iteration, chunks != null ? chunks.size() : 0, durationMs);
        return chunks;
    }

    private ChatResponseDto buildToolResponse(String model, ChatMessageDto toolMessage) {
        return ChatResponseDto.builder()
                .model(model)
                .createdAt(Instant.now().toString())
                .message(toolMessage)
                .done(false)
                .build();
    }

    private void handleChunk(OllamaRequestHandler ctx, ChatResponseDto resp) {
        ChatMessageDto message = resp.getMessage();
        if (message != null) {
            if (message.getThinking() != null && !message.getThinking().isBlank()) {
                ctx.log("thinking", message.getThinking());
            }
            if (message.getContent() != null && !message.getContent().isBlank()) {
                ctx.log("response", message.getContent());
            }
        }
        if (resp.isDone()) {
            ctx.logDone(null);
        }
        ctx.next();
    }

    private void logToolCalls(OllamaRequestHandler ctx, List<ToolCallDto> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        toolCalls.forEach(call -> {
            if (call.getFunction() == null) {
                ctx.log("tool_call", "Received tool call with no function payload");
                return;
            }
            ctx.log("tool_call", "function=%s arguments=%s".formatted(
                    call.getFunction().getName(),
                    call.getFunction().getArguments()));
        });
    }

    private static ChatMessageDto findLastMessage(List<ChatResponseDto> chunks) {
        for (int i = chunks.size() - 1; i >= 0; i--) {
            ChatMessageDto message = chunks.get(i).getMessage();
            if (message != null) {
                return message;
            }
        }
        return null;
    }

    private static ChatMessageDto findLastMessageWithToolCalls(List<ChatResponseDto> chunks) {
        for (int i = chunks.size() - 1; i >= 0; i--) {
            ChatMessageDto message = chunks.get(i).getMessage();
            if (message != null && message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                return message;
            }
        }
        return null;
    }
}
