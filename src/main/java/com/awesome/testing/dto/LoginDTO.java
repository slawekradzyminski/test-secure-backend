package com.awesome.testing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

import jakarta.validation.constraints.Size;

@Value
public class LoginDTO {

    @Size(min = 3, max = 255, message = "Minimum username length: 3 characters")
    @Schema(description = "Username", example = "admin", required = true)
    String username;

    @Size(min = 3, max = 255, message = "Minimum password length: 3 characters")
    @Schema(description = "Password", example = "admin", required = true)
    String password;

}