package com.awesome.testing.dto.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {

    @NotNull(message = "Username is required")
    @Size(min = 4, message = "Minimum username length: 4 characters")
    @Size(max = 255, message = "Maximum username length: 255 characters")
    @Schema(minLength = 4, maxLength = 255, description = "Username", example = "johndoe")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Schema(minLength = 1, pattern = "\\S", description = "Email address", example = "john.doe@example.com")
    private String email;

    @NotNull(message = "Password is required")
    @Size(min = 8, message = "Minimum password length: 8 characters")
    @Size(max = 255, message = "Maximum password length: 255 characters")
    @Schema(minLength = 8, maxLength = 255, description = "Password", example = "password123")
    private String password;

    @NotNull(message = "firstName is required")
    @Size(min = 4, message = "Minimum firstName length: 4 characters")
    @Size(max = 255, message = "Maximum firstName length: 255 characters")
    @Schema(minLength = 4, maxLength = 255, description = "First name", example = "John")
    private String firstName;

    @NotNull(message = "lastName is required")
    @Size(min = 4, message = "Minimum lastName length: 4 characters")
    @Size(max = 255, message = "Maximum lastName length: 255 characters")
    @Schema(minLength = 4, maxLength = 255, description = "Last name", example = "Boyd")
    private String lastName;

    @JsonIgnore
    @Schema(hidden = true)
    private List<Role> roles;

}
