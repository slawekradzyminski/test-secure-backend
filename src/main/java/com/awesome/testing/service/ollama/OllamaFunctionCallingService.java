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
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

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

        List<ChatMessageDto> history = new ArrayList<>(request.getMessages());
        OllamaRequestHandler ctx = new OllamaRequestHandler("chat-tools", request.getModel());

        return runIteration(request, history, 0, ctx);
    }

    private Flux<ChatResponseDto> runIteration(ChatRequestDto baseRequest,
                                                List<ChatMessageDto> history,
                                                int iteration,
                                                OllamaRequestHandler ctx) {
        if (iteration >= MAX_TOOL_CALL_ITERATIONS) {
            log.error("Exceeded maximum tool call iterations ({}) for model {}", MAX_TOOL_CALL_ITERATIONS, baseRequest.getModel());
            return Flux.error(new IllegalStateException("Exceeded maximum tool call iterations"));
        }

        log.info("Iteration {}: forwarding {} historical messages to Ollama (tools={})",
                iteration + 1, history.size(), baseRequest.getTools().size());

        Sinks.Many<ChatResponseDto> replaySink = Sinks.many().replay().all();
        List<ChatResponseDto> collectedChunks = new ArrayList<>();

        ChatRequestDto iterationRequest = ChatRequestDto.builder()
                .model(baseRequest.getModel())
                .messages(List.copyOf(history))
                .options(baseRequest.getOptions())
                .tools(baseRequest.getTools())
                .stream(baseRequest.getStream())
                .keepAlive(baseRequest.getKeepAlive())
                .think(baseRequest.getThink())
                .build();

        Flux<ChatResponseDto> ollamaStream = ollamaWebClient.post()
                .uri("/api/chat")
                .bodyValue(iterationRequest)
                .retrieve()
                .bodyToFlux(ChatResponseDto.class)
                .doOnNext(resp -> {
                    logToolCalls(ctx, resp.getMessage() != null ? resp.getMessage().getToolCalls() : null);
                    handleChunk(ctx, resp);
                    collectedChunks.add(resp);
                    replaySink.tryEmitNext(resp);
                })
                .doOnError(e -> {
                    ctx.logError(e);
                    replaySink.tryEmitError(e);
                })
                .doOnComplete(() -> {
                    ctx.logComplete();
                    replaySink.tryEmitComplete();
                });

        Mono<List<ChatResponseDto>> collected = ollamaStream
                .collectList()
                .map(chunks -> {
                    log.info("Iteration {}: Ollama responded with {} chunk(s)", iteration + 1, chunks.size());
                    return chunks;
                });

        return Flux.merge(
                replaySink.asFlux(),
                collected.flatMapMany(chunks -> processIterationResult(baseRequest, history, chunks, iteration, ctx))
        );
    }

    private Flux<ChatResponseDto> processIterationResult(ChatRequestDto baseRequest,
                                                          List<ChatMessageDto> history,
                                                          List<ChatResponseDto> chunks,
                                                          int iteration,
                                                          OllamaRequestHandler ctx) {
        if (chunks == null || chunks.isEmpty()) {
            log.warn("Iteration {}: Ollama returned no chunks, completing stream", iteration + 1);
            return Flux.empty();
        }

        ChatMessageDto toolRequestMessage = findLastMessageWithToolCalls(chunks);
        if (toolRequestMessage != null) {
            history.add(toolRequestMessage);
            List<String> requestedTools = toolRequestMessage.getToolCalls().stream()
                    .map(call -> call.getFunction().getName())
                    .collect(Collectors.toList());
            log.info("Iteration {}: Ollama requested {} tool call(s): {}",
                    iteration + 1, requestedTools.size(), requestedTools);

            List<ChatResponseDto> toolResponses = new ArrayList<>();
            for (ToolCallDto toolCall : toolRequestMessage.getToolCalls()) {
                String toolName = toolCall.getFunction().getName();
                log.info("Iteration {}: Executing tool {} with args {}", iteration + 1, toolName,
                        toolCall.getFunction().getArguments());
                ChatMessageDto toolOutput = toolRegistry.execute(toolCall);
                log.info("Iteration {}: Tool {} responded (payload {} chars)",
                        iteration + 1, toolName,
                        toolOutput.getContent() != null ? toolOutput.getContent().length() : 0);
                history.add(toolOutput);
                toolResponses.add(buildToolResponse(baseRequest.getModel(), toolOutput));
            }

            return Flux.concat(
                    Flux.fromIterable(toolResponses),
                    runIteration(baseRequest, history, iteration + 1, ctx)
            );
        }

        ChatMessageDto assistantMessage = findLastMessage(chunks);
        if (assistantMessage != null) {
            history.add(assistantMessage);
            log.info("Iteration {}: Received assistant completion ({} chars). Ending stream.",
                    iteration + 1, assistantMessage.getContent() != null ? assistantMessage.getContent().length() : 0);
        }

        return Flux.empty();
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
