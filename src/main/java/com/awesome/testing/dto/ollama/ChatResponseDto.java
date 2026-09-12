package com.awesome.testing.dto.ollama;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {
    @Schema(types = {"string", "null"})
    private String model;
    
    @JsonProperty("created_at")
    @Schema(types = {"string", "null"})
    private String createdAt;
    
    @Schema(types = {"object", "null"}, description = "Message chunk; may be null when an upstream event contains no message")
    private ChatMessageDto message;
    private boolean done;

} 