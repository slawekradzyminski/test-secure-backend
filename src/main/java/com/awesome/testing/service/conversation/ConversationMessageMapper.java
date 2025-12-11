package com.awesome.testing.service.conversation;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.ollama.ChatMessageDto;
import com.awesome.testing.dto.ollama.ToolCallDto;
import com.awesome.testing.entity.ConversationMessageEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ConversationMessageMapper {

    private static final TypeReference<List<ToolCallDto>> TOOL_CALL_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public ChatMessageDto toDto(ConversationMessageEntity message) {
        return ChatMessageDto.builder()
                .role(message.getRole())
                .content(message.getContent())
                .thinking(message.getThinking())
                .toolName(message.getToolName())
                .toolCalls(parseToolCalls(message.getToolCallsJson()))
                .build();
    }

    public void applyToolCalls(ConversationMessageEntity entity, List<ToolCallDto> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            entity.setToolCallsJson(null);
            return;
        }
        try {
            entity.setToolCallsJson(objectMapper.writeValueAsString(toolCalls));
        } catch (JsonProcessingException e) {
            throw new CustomException("Failed to store tool calls", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<ToolCallDto> parseToolCalls(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, TOOL_CALL_LIST);
        } catch (JsonProcessingException e) {
            throw new CustomException("Failed to parse stored conversation", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
