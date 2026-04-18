package com.awesome.testing.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SsoExchangeRequestDto {

    @NotBlank
    @Schema(description = "OIDC ID token returned by the configured identity provider")
    private String idToken;

}
