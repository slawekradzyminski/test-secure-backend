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

    @Schema(description = "Email address. Shown in full to administrators and for the caller's own account; "
            + "other accounts' addresses are masked for other callers, keeping the first character and the "
            + "domain (j***@example.com).", example = "john.doe@example.com")
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

    /**
     * The directory view of an account for one caller (catalog X-12): administrators and the account itself
     * see the email address; every other caller sees it masked. The value stays a string.
     */
    public static UserResponseDto forViewer(UserEntity user, String viewerUsername, boolean viewerIsAdmin) {
        UserResponseDto dto = from(user);
        if (!viewerIsAdmin && !user.getUsername().equals(viewerUsername)) {
            dto.setEmail(maskEmail(user.getEmail()));
        }
        return dto;
    }

    static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.lastIndexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

}
