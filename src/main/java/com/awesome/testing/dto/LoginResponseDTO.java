package com.awesome.testing.dto;

import com.awesome.testing.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LoginResponseDTO {

    @Schema(description = "Username", example = "user", required = true)
    String username;

    @Schema(description = "List of roles", required = true)
    List<Role> roles;

    @Schema(description = "First name", example = "John", required = true)
    String firstName;

    @Schema(description = "Last name", example = "Doe", required = true)
    String lastName;

    @Schema(description = "Token", example = "token", required = true)
    String token;

    @Schema(description = "Email", example = "user@example.com", required = true)
    String email;

}