package com.awesome.testing.dto;

import com.awesome.testing.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
public class UserRegisterDTO {

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @Schema(description = "Username", example = "johndoe")
    private String username;

    @Email(message = "Email should be valid")
    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Size(min = 4, message = "Minimum password length: 4 characters")
    @Schema(description = "Password", example = "password123")
    private String password;

    @Size(max = 255)
    @Schema(description = "First name", example = "John")
    private String firstName;

    @Size(max = 255)
    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "User roles", example = "[\"ROLE_USER\"]")
    @NotEmpty(message = "User must have at least one role")
    private List<Role> roles;

}
