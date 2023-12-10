package com.awesome.testing.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import com.awesome.testing.model.Role;

@Setter
@Getter
@ToString
public class UserResponseDTO {

    @Schema(description = "User ID", example = "1", required = true)
    private Integer id;

    @Schema(description = "Username", example = "user", required = true)
    private String username;

    @Schema(description = "Email", example = "user@example.com", required = true)
    private String email;

    @Schema(description = "List of roles", required = true)
    private List<Role> roles;

    @Schema(description = "First name", example = "John", required = true)
    private String firstName;

    @Schema(description = "Last name", example = "Doe", required = true)
    private String lastName;

}