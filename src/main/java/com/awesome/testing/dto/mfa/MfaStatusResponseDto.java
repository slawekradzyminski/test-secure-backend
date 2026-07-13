package com.awesome.testing.dto.mfa;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaStatusResponseDto {

    @Schema(description = "Whether application-owned TOTP is enabled")
    boolean enabled;

    @Schema(description = "Number of unused one-time recovery codes")
    long unusedRecoveryCodes;
}
