package com.awesome.testing.dto;

import com.awesome.testing.model.Role;
import com.awesome.testing.model.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

    @Schema(description = "User ID", example = "1")
    Integer id;

    @Schema(description = "Username", example = "johndoe")
    String username;

    @Schema(description = "Email address", example = "john.doe@example.com")
    String email;

    @Schema(description = "User roles", example = "[\"ROLE_CLIENT\"]")
    List<Role> roles;

    @Schema(description = "First name", example = "John")
    String firstName;

    @Schema(description = "Last name", example = "Boyd")
    String lastName;

    public static UserResponseDto from(User user) {
        return UserResponseDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(user.getRoles())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .build();
    }

}
