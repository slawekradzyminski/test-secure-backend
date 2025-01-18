package com.awesome.testing.dto;

import com.awesome.testing.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class LoginResponseDTO {

    @Schema(description = "JWT token", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    String token;

    @Schema(description = "Username", example = "johndoe")
    String username;

    @Schema(description = "Email address", example = "john.doe@example.com")
    String email;

    @Schema(description = "First name", example = "John")
    String firstName;

    @Schema(description = "Last name", example = "Doe")
    String lastName;

    @Schema(description = "User roles", example = "[\"ROLE_USER\"]")
    List<Role> roles;

}
