package com.awesome.testing.dto.mfa;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaRecoveryCodesResponseDto {

    @Schema(description = "One-time recovery codes. These are returned only when created.")
    List<String> recoveryCodes;
}
