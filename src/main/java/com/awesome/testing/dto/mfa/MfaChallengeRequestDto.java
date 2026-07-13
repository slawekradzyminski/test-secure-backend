package com.awesome.testing.dto.mfa;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaChallengeRequestDto {

    @NotBlank
    @Size(max = 255)
    @Schema(description = "Short-lived challenge returned after password verification")
    private String challengeToken;

    @NotBlank
    @Size(min = 6, max = 32)
    @Schema(description = "Six-digit authenticator code or one-time recovery code")
    private String code;
}
