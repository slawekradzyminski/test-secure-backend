package com.awesome.testing.dto.email;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDto {

    @Schema(description = "Email recipient", example = "user@example.com")
    @Email(message = "Invalid email format")
    private String to;
    
    @Schema(description = "Email subject", example = "Important message")
    @NotBlank(message = "Email subject is required")
    private String subject;
    
    @Schema(description = "Email content", example = "Please read this message carefully")
    @NotBlank(message = "Email content is required")
    private String message;
}
