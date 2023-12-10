package com.awesome.testing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import jakarta.validation.constraints.Size;

@Value
public class LoginDTO {

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @Schema(description = "Username", example = "user", required = true)
    String username;

    @Size(min = 4, max = 255, message = "Minimum password length: 4 characters")
    @Schema(description = "Password", example = "pass", required = true)
    String password;

}