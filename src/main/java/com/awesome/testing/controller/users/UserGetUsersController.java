package com.awesome.testing.controller.users;

import com.awesome.testing.controller.doc.UnauthorizedApiResponse;
import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
    @Operation(summary = "Get all users", security = @SecurityRequirement(name = "bearerAuth"))
    @UnauthorizedApiResponse
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of users",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserResponseDto.class))))
    })
    public List<UserResponseDto> getAll() {
        return userService.getAll().stream()
                .map(UserResponseDto::from)
                .toList();
    }

}
