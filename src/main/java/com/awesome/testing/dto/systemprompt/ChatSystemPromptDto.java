package com.awesome.testing.dto.systemprompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSystemPromptDto {

    @Size(max = 5000, message = "Chat system prompt must be at most 5000 characters")
    @Schema(description = "General chat system prompt", example = "You are a helpful assistant.")
    private String chatSystemPrompt;
}
