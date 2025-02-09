package com.awesome.testing.controller.users;

import com.awesome.testing.dto.UserResponseDto;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserGetController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get all users", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of users",
                    content = @Content(schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – Missing or invalid token", content = @Content)
    })
    public List<UserResponseDto> getAll() {
        return userService.getAll().stream()
                .map(UserResponseDto::from)
                .toList();
    }

    @GetMapping("/{username}")
    @Operation(summary = "Get user by username", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User details",
                    content = @Content(schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Unauthorized – Missing or invalid token", content = @Content),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist", content = @Content)
    })
    public UserResponseDto getByUsername(@Parameter(description = "Username") @PathVariable String username) {
        User user = userService.search(username);
        return UserResponseDto.from(user);
    }

}
