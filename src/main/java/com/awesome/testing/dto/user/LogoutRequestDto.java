package com.awesome.testing.dto.user;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequestDto {

    @NotBlank
    @Schema(description = "Refresh token to revoke during logout")
    private String refreshToken;
}
