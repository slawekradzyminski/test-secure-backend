package com.awesome.testing.dto.conversation;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequestDto {
    @NotNull
    private ConversationType type;

    @Size(max = 255)
    private String title;

    @Size(max = 255)
    private String model;

    private Double temperature;

    private Boolean think;
}
