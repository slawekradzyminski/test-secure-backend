package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.UserResponseDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserMeController {

    private final UserService userService;

    @GetMapping("/me")
    @Operation(summary = "Get current user information",
            description = "Returns account details for the authenticated user resolved from the JWT token.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Current user details")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    public UserResponseDto whoAmI(HttpServletRequest req) {
        UserEntity user = userService.whoAmI(req);
        return UserResponseDto.from(user);
    }

}
