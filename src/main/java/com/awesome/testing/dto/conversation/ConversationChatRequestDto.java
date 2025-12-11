package com.awesome.testing.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationChatRequestDto {

    @NotBlank
    private String content;

    private String model;

    private Double temperature;

    private Boolean think;

    private Map<String, Object> options;
}
