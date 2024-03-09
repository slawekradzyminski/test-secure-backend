package com.awesome.testing.dto.users;

import java.util.List;

import com.awesome.testing.dto.specialty.SpecialtyDto;
import com.awesome.testing.entities.user.UserEntity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Setter
@Getter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponseDto {

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

    private List<SpecialtyDto> specialties;

    public static UserResponseDto from(UserEntity entity) {
        return UserResponseDto.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .roles(entity.getRoles())
                .specialties(SpecialtyDto.from(entity.getSpecialties()))
                .build();
    }

}