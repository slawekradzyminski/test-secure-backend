package com.awesome.testing.controller.users;

import com.awesome.testing.controller.doc.UnauthorizedApiResponse;
import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.entity.UserEntity;
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

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserGetSingleUserController {

    private final UserService userService;

    @GetMapping("/{username}")
    @Operation(summary = "Get user by username", security = @SecurityRequirement(name = "bearerAuth"))
    @UnauthorizedApiResponse
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User details",
                    content = @Content(schema = @Schema(implementation = UserResponseDto.class))),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist", content = @Content)
    })
    public UserResponseDto getByUsername(@Parameter(description = "Username") @PathVariable String username) {
        UserEntity user = userService.search(username);
        return UserResponseDto.from(user);
    }

}
