package com.awesome.testing.controller.users;

import com.awesome.testing.dto.UserEditDto;
import com.awesome.testing.model.User;
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

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserEditController {

    private final UserService userService;

    @PutMapping("/{username}")
    @PreAuthorize("@userService.exists(#username) and (hasRole('ROLE_ADMIN') or #username == authentication.principal.username)")
    @Operation(summary = "Update user", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User was updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized – Missing or invalid token", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden – Insufficient permissions", content = @Content),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist", content = @Content)
    })
    public User edit(
            @Parameter(description = "Username") @PathVariable String username,
            @Parameter(description = "User details") @Valid @RequestBody UserEditDto userDto) {
        return userService.edit(username, userDto);
    }

}
