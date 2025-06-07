package com.awesome.testing.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
public class UserEditDto {

    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is required")
    @Schema(description = "Email address", example = "john.doe@example.com")
    private String email;

    @Size(min = 4, max = 255, message = "Minimum firstName length: 4 characters")
    @Schema(description = "First name", example = "John")
    private String firstName;

    @Size(min = 4, max = 255, message = "Minimum lastName length: 4 characters")
    @Schema(description = "Last name", example = "Boyd")
    private String lastName;

}
