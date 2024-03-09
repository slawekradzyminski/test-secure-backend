package com.awesome.testing.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginDto {

    @Size(min = 3, max = 255, message = "Minimum username length: 3 characters")
    @Schema(description = "Username", example = "admin", required = true)
    String username;

    @Size(min = 3, max = 255, message = "Minimum password length: 3 characters")
    @Schema(description = "Password", example = "admin", required = true)
    String password;

}