package com.awesome.testing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDTO {
    @Schema(description = "Email recipient", example = "user@example.com")
    private String recipient;
    
    @Schema(description = "Email subject", example = "Important message")
    private String subject;
    
    @Schema(description = "Email content", example = "Please read this message carefully")
    private String content;
}
