package com.awesome.testing.dto.users;

import com.awesome.testing.dto.specialty.SpecialtyDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

import com.awesome.testing.entities.user.UserEntity;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDto {

    @Schema(description = "Username", example = "user", required = true)
    String username;

    @Schema(description = "List of roles", required = true)
    List<Role> roles;

    @Schema(description = "First name", example = "John", required = true)
    String firstName;

    @Schema(description = "Last name", example = "Doe", required = true)
    String lastName;

    @Schema(description = "Token", example = "token", required = true)
    String token;

    @Schema(description = "Email", example = "user@example.com", required = true)
    String email;

    List<SpecialtyDto> specialties;

    public static LoginResponseDto from(UserEntity userEntity, String token) {
        return LoginResponseDto.builder()
                .username(userEntity.getUsername())
                .firstName(userEntity.getFirstName())
                .lastName(userEntity.getLastName())
                .roles(userEntity.getRoles())
                .token(token)
                .email(userEntity.getEmail())
                .specialties(SpecialtyDto.from(userEntity.getSpecialties()))
                .build();
    }
}