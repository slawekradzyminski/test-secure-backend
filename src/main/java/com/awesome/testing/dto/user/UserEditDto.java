package com.awesome.testing.dto.user;

import com.awesome.testing.dto.validation.VisibleText;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEditDto {

    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is required")
    @Schema(minLength = 1, pattern = "\\S", description = "Email address", example = "john.doe@example.com")
    private String email;

    @VisibleText(message = "firstName must not be blank")
    @Size(min = 4, message = "Minimum firstName length: 4 characters")
    @Size(max = 255, message = "Maximum firstName length: 255 characters")
    @Schema(minLength = 4, maxLength = 255, pattern = "\\S", types = {"string", "null"}, description = "First name", example = "John")
    private String firstName;

    @VisibleText(message = "lastName must not be blank")
    @Size(min = 4, message = "Minimum lastName length: 4 characters")
    @Size(max = 255, message = "Maximum lastName length: 255 characters")
    @Schema(minLength = 4, maxLength = 255, pattern = "\\S", types = {"string", "null"}, description = "Last name", example = "Boyd")
    private String lastName;

}
