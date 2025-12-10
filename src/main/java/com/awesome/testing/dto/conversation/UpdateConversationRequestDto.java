package com.awesome.testing.dto.conversation;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationRequestDto {
    @Size(max = 255)
    private String title;

    private Boolean archived;
}
