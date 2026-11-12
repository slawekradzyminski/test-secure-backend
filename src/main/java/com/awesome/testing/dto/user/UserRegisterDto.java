package com.awesome.testing.dto.user;

import com.awesome.testing.dto.validation.MaxUtf8Bytes;
import com.awesome.testing.dto.validation.VisibleText;
import com.awesome.testing.security.PasswordPolicy;
import com.awesome.testing.service.identity.UserIdentity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterDto {

    @NotNull(message = "Username is required")
    @VisibleText(message = "Username is required")
    @Size(min = 4, message = "Minimum username length: 4 characters")
    @Size(max = 255, message = "Maximum username length: 255 characters")
    @Pattern(regexp = UserIdentity.USERNAME_CHARACTERS, message = UserIdentity.USERNAME_CHARACTERS_MESSAGE)
    @Schema(minLength = 4, maxLength = 255, pattern = "^[A-Za-z0-9._-]{4,255}$",
            description = "Username of letters, digits, dots, underscores and hyphens; unique regardless of letter case",
            example = "johndoe")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    @Schema(minLength = 1, pattern = "\\S",
            description = "Email address; stored lower-case and unique regardless of letter case",
            example = "john.doe@example.com")
    private String email;

    @NotNull(message = "Password is required")
    @VisibleText(message = "Password is required")
    @Size(min = 8, message = "Minimum password length: 8 characters")
    @MaxUtf8Bytes(value = PasswordPolicy.MAX_BYTES, message = PasswordPolicy.MAX_BYTES_MESSAGE)
    @Schema(minLength = 8, maxLength = 72, pattern = "\\S",
            description = "Password of at least 8 characters and at most 72 bytes in UTF-8 (the BCrypt limit; "
                    + "a character outside ASCII takes 2 to 4 bytes)", example = "password123")
    private String password;

    @NotNull(message = "firstName is required")
    @VisibleText(message = "firstName is required")
    @Size(min = 4, message = "Minimum firstName length: 4 characters")
    @Size(max = 255, message = "Maximum firstName length: 255 characters")
    @Schema(minLength = 4, maxLength = 255, pattern = "\\S", description = "First name", example = "John")
    private String firstName;

    @NotNull(message = "lastName is required")
    @VisibleText(message = "lastName is required")
    @Size(min = 4, message = "Minimum lastName length: 4 characters")
    @Size(max = 255, message = "Maximum lastName length: 255 characters")
    @Schema(minLength = 4, maxLength = 255, pattern = "\\S", description = "Last name", example = "Boyd")
    private String lastName;

    @JsonIgnore
    @Schema(hidden = true)
    private List<Role> roles;

}
