package com.awesome.testing.dto;

import com.awesome.testing.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Data
@Builder
public class UserRegisterDto {

    @NotNull(message = "Username is required")
    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @Schema(description = "Username", example = "johndoe")
    private String username;

    @NotNull(message = "Email is required")
    @Email(message = "Email should be valid")
    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @NotNull(message = "Password is required")
    @Size(min = 8, max = 255, message = "Minimum password length: 8 characters")
    @Schema(description = "Password", example = "password123")
    private String password;

    @Size(min = 4, max = 255, message = "Minimum firstName length: 4 characters")
    @Schema(description = "First name", example = "John")
    private String firstName;

    @Size(min = 4, max = 255, message = "Minimum lastName length: 4 characters")
    @Schema(description = "Last name", example = "Boyd")
    private String lastName;

    @NotEmpty(message = "At least one role must be specified")
    @Schema(description = "User roles", example = "[\"ROLE_CLIENT\"]")
    private List<Role> roles;

}
