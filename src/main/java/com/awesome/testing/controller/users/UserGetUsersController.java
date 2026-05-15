package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserGetUsersController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Get all users",
            description = "Returns all user accounts visible to the authenticated caller.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "List of users")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public List<UserResponseDto> getAll() {
        return userService.getAll().stream()
                .map(UserResponseDto::from)
                .toList();
    }

}
