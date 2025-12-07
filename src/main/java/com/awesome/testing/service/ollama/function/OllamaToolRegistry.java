package com.awesome.testing.service.ollama.function;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.dto.ollama.ToolCallFunctionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OllamaToolRegistry {

    private final Map<String, FunctionCallHandler> handlerByName;

    public OllamaToolRegistry(List<FunctionCallHandler> handlers) {
        this.handlerByName = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(FunctionCallHandler::getName, h -> h));
        log.info("Registered {} Ollama tool handlers: {}", handlerByName.size(), handlerByName.keySet());
    }

    public ChatMessageDto execute(ToolCallDto toolCall) {
        Objects.requireNonNull(toolCall, "toolCall must not be null");
        ToolCallFunctionDto function = toolCall.getFunction();
        if (function == null) {
            throw new IllegalArgumentException("Tool call missing function payload");
        }
        String functionName = function.getName();
        FunctionCallHandler handler = handlerByName.get(functionName);
        if (handler == null) {
            throw new IllegalArgumentException("Unsupported tool: " + functionName);
        }
        log.info("Executing tool {} with args {}", functionName, function.getArguments());
        return handler.handle(toolCall);
    }
}
