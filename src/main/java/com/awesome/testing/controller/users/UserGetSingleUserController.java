package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.awesome.testing.dto.ErrorDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.awesome.testing.security.CustomPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserGetSingleUserController {

    private final UserService userService;

    @GetMapping("/{username}")
    @Operation(summary = "Get user by username",
            description = "Returns public account details for the requested username. The email address is masked (j***@example.com) unless the caller is an administrator or the account itself.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "User details")
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "The user doesn't exist",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public UserResponseDto getByUsername(@Parameter(description = "Username") @PathVariable String username,
                                         @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        UserEntity user = userService.search(username);
        return UserResponseDto.forViewer(user, principal.getUsername(), principal.isAdmin());
    }

}
