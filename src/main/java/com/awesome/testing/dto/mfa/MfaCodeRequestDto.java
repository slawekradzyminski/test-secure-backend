package com.awesome.testing.dto.mfa;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaCodeRequestDto {

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "Authenticator code must contain exactly 6 digits")
    @Schema(description = "Six-digit code from an authenticator app", example = "123456")
    private String code;
}
