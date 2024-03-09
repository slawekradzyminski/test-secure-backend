package com.awesome.testing.dto.users;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEditDto {

    @NotEmpty
    @Email
    @Schema(description = "Email", example = "user@example.com", required = true)
    String email;

    @NotEmpty(message = "Please pick at least one role")
    @Schema(description = "List of roles", required = true)
    List<Role> roles;

    @Size(min = 3, max = 255, message = "Minimum firstName length: 3 characters")
    @Schema(description = "First name", example = "John", required = true)
    String firstName;

    @Size(min = 3, max = 255, message = "Minimum lastName length: 3 characters")
    @Schema(description = "Last name", example = "Doe", required = true)
    String lastName;

}