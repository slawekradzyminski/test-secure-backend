package com.awesome.testing.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginDto {

    @NotEmpty(message = "Username is required")
    @Size(min = 4, message = "Minimum username length: 4 characters")
    @Size(max = 255, message = "Maximum username length: 255 characters")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 4, maxLength = 255, description = "Username", example = "admin")
    private String username;

    @NotEmpty(message = "Password is required")
    @Size(min = 4, message = "Minimum password length: 4 characters")
    @Size(max = 255, message = "Maximum password length: 255 characters")
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 4, maxLength = 255, description = "Password", example = "admin")
    private String password;

}
