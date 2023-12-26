package com.awesome.testing.dto.users;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {

    @Size(min = 3, max = 255, message = "Minimum username length: 3 characters")
    @Schema(description = "Username", example = "user", required = true)
    private String username;

    @NotEmpty
    @Email
    @Schema(description = "Email", example = "user@example.com", required = true)
    private String email;

    @Size(min = 3, max = 255, message = "Minimum password length: 3 characters")
    @Schema(description = "Password", example = "pass", required = true)
    private String password;

    @NotEmpty(message = "Please pick at least one role")
    @Schema(description = "List of roles", required = true)
    private List<Role> roles;

    @Size(min = 3, max = 255, message = "Minimum firstName length: 3 characters")
    @Schema(description = "First name", example = "John", required = true)
    private String firstName;

    @Size(min = 3, max = 255, message = "Minimum lastName length: 3 characters")
    @Schema(description = "Last name", example = "Doe", required = true)
    private String lastName;

}