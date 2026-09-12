package com.awesome.testing.dto.user;

import com.awesome.testing.entity.UserEntity;
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

    @Schema(types = {"string", "null"}, description = "First name", example = "John")
    String firstName;

    @Schema(types = {"string", "null"}, description = "Last name", example = "Boyd")
    String lastName;

    public static UserResponseDto from(UserEntity user) {
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
