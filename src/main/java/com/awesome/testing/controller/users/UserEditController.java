package com.awesome.testing.controller.users;

import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.ErrorDto;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import com.awesome.testing.dto.user.UserEditDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
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
    // Only administrators learn whether another username exists (404); everyone else gets 403 for any
    // username but their own.
    @PreAuthorize("hasRole('ROLE_ADMIN') ? @userService.exists(#username) : #username == authentication.principal.username")
    @Operation(summary = "Update user",
            description = "Updates editable profile fields for an existing user. Users may edit themselves; administrators may edit any user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "User was updated")
    @ApiResponse(responseCode = "400", description = "Invalid fields, or the email is already used by another account",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "A non-administrator addressed any username other than their own",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "The user doesn't exist (administrators only)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "A concurrent change claimed the same email",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public UserEntity edit(
            @Parameter(description = "Username") @PathVariable String username,
            @Parameter(description = "User details") @Valid @RequestBody UserEditDto userDto) {
        return userService.edit(username, userDto);
    }

}
