package com.awesome.testing.service.ollama;

import com.awesome.testing.dto.ollama.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.BooleanSupplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class OllamaService {

    private final WebClient ollamaWebClient;

    public Flux<GenerateResponseDto> generateText(StreamedRequestDto req) {
        OllamaRequestHandler ctx = new OllamaRequestHandler("generate-text", req.getModel());

        return ollamaWebClient.post()
                .uri("/api/generate")
                .bodyValue(toGenerateRequest(req))
                .retrieve()
                .bodyToFlux(GenerateResponseDto.class)
                .doOnNext(resp -> handleChunk(ctx,
                        resp.getResponse(),
                        resp.getThinking(),
                        resp::isDone,
                        resp.getTotalDuration()))
                .doOnError(ctx::logError)
                .doOnComplete(ctx::logComplete);
    }

    public Flux<ChatResponseDto> chat(ChatRequestDto req) {
        OllamaRequestHandler ctx = new OllamaRequestHandler("chat", req.getModel());

        return ollamaWebClient.post()
                .uri("/api/chat")
                .bodyValue(req)
                .retrieve()
                .bodyToFlux(ChatResponseDto.class)
                .doOnNext(resp -> {
                    ChatMessageDto message = resp.getMessage();
                    if (message != null) {
                        logToolCalls(ctx, message.getToolCalls());
                    }
                    handleChunk(ctx,
                            message != null ? message.getContent() : null,
                            message != null ? message.getThinking() : null,
                            resp::isDone,
                            null);
                })
                .doOnError(ctx::logError)
                .doOnComplete(ctx::logComplete);
    }

    private void logToolCalls(OllamaRequestHandler ctx, List<ToolCallDto> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        toolCalls.forEach(call -> {
            ToolCallFunctionDto function = call.getFunction();
            if (function == null) {
                ctx.log("tool_call", "Received tool call with no function payload");
                return;
            }
            ctx.log("tool_call", "function=%s arguments=%s".formatted(
                    function.getName(),
                    function.getArguments()));
        });
    }

    private void handleChunk(OllamaRequestHandler ctx,
                             String content,
                             String thinking,
                             BooleanSupplier isDone,
                             Long totalDurationNanos) {

        if (thinking != null && !thinking.isBlank()) {
            ctx.log("thinking", thinking);
        }
        if (content != null && !content.isBlank()) {
            ctx.log("response", content);
        }

        if (isDone.getAsBoolean()) {
            ctx.logDone(totalDurationNanos);
        }
        ctx.next();
    }

    private static GenerateRequestDto toGenerateRequest(StreamedRequestDto streamedRequestDto) {
        return GenerateRequestDto.builder()
                .model(streamedRequestDto.getModel())
                .prompt(streamedRequestDto.getPrompt())
                .stream(true)
                .options(streamedRequestDto.getOptions())
                .think(streamedRequestDto.getThink())
                .build();
    }

}
