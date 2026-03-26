package com.awesome.testing.controller.users;

import com.awesome.testing.controller.doc.ForbiddenApiResponse;
import com.awesome.testing.controller.doc.UnauthorizedApiResponse;
import com.awesome.testing.dto.user.UserEditDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserEditController {

    private final UserService userService;

    @PutMapping("/{username}")
    @PreAuthorize("@userService.exists(#username) and (hasRole('ROLE_ADMIN') or #username == authentication.principal.username)")
    @Operation(summary = "Update user", security = @SecurityRequirement(name = "bearerAuth"))
    @UnauthorizedApiResponse
    @ForbiddenApiResponse
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User was updated"),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist", content = @Content)
    })
    public UserEntity edit(
            @Parameter(description = "Username") @PathVariable String username,
            @Parameter(description = "User details") @Valid @RequestBody UserEditDto userDto) {
        return userService.edit(username, userDto);
    }

}
