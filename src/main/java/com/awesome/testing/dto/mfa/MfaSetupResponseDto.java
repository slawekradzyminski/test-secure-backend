package com.awesome.testing.dto.mfa;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaSetupResponseDto {

    @Schema(description = "Manual Base32 setup key")
    String secret;

    @Schema(description = "Standard OTPAuth URI")
    String otpAuthUri;

    @Schema(description = "PNG QR code encoded as a data URI")
    String qrCodeDataUri;

    @Schema(description = "Time at which this unconfirmed setup expires")
    Instant expiresAt;
}
