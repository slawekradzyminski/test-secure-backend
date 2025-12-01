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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

        return Flux.create(sink -> runConversation(request, sink), FluxSink.OverflowStrategy.BUFFER);
    }

    private void runConversation(ChatRequestDto baseRequest, FluxSink<ChatResponseDto> sink) {
        List<ChatMessageDto> history = new ArrayList<>(baseRequest.getMessages());
        try {
            for (int iteration = 0; iteration < MAX_TOOL_CALL_ITERATIONS; iteration++) {
                log.debug("Starting tool-enabled iteration {}", iteration + 1);
                List<ChatResponseDto> iterationChunks = sendToOllama(baseRequest, history, sink);
                if (iterationChunks == null || iterationChunks.isEmpty()) {
                    sink.complete();
                    return;
                }

                ChatMessageDto toolRequestMessage = findLastMessageWithToolCalls(iterationChunks);
                if (toolRequestMessage != null) {
                    history.add(toolRequestMessage);
                    for (ToolCallDto toolCall : toolRequestMessage.getToolCalls()) {
                        ChatMessageDto toolOutput = toolRegistry.execute(toolCall);
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

                sink.complete();
                return;
            }
            sink.error(new IllegalStateException("Exceeded maximum tool call iterations"));
        } catch (Throwable t) {
            sink.error(t);
        }
    }

    private List<ChatResponseDto> sendToOllama(ChatRequestDto baseRequest,
                                               List<ChatMessageDto> history,
                                               FluxSink<ChatResponseDto> sink) {
        ChatRequestDto iterationRequest = ChatRequestDto.builder()
                .model(baseRequest.getModel())
                .messages(List.copyOf(history))
                .options(baseRequest.getOptions())
                .tools(baseRequest.getTools())
                .stream(baseRequest.getStream())
                .keepAlive(baseRequest.getKeepAlive())
                .think(baseRequest.getThink())
                .build();

        return ollamaWebClient.post()
                .uri("/api/chat")
                .bodyValue(iterationRequest)
                .retrieve()
                .bodyToFlux(ChatResponseDto.class)
                .doOnNext(sink::next)
                .collectList()
                .block();
    }

    private ChatResponseDto buildToolResponse(String model, ChatMessageDto toolMessage) {
        return ChatResponseDto.builder()
                .model(model)
                .createdAt(Instant.now().toString())
                .message(toolMessage)
                .done(false)
                .build();
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
