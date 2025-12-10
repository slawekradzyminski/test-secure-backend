package com.awesome.testing.dto.conversation;

import com.awesome.testing.dto.ollama.ChatMessageDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailDto {
    private ConversationSummaryDto summary;
    private String systemPromptSnapshot;
    private List<ChatMessageDto> messages;
}
