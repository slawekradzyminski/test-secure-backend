package com.awesome.testing.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@Builder
public class UserEditDto {

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @Schema(description = "Username", example = "johndoe")
    private String username;

    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is required")
    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Size(min = 4, max = 255, message = "Minimum firstName length: 4 characters")
    @Schema(description = "First name", example = "John")
    private String firstName;

    @Size(min = 4, max = 255, message = "Minimum lastName length: 4 characters")
    @Schema(description = "Last name", example = "Doe")
    private String lastName;

    @Schema(description = "User roles", example = "[\"ROLE_CLIENT\"]")
    private List<Role> roles;

}
