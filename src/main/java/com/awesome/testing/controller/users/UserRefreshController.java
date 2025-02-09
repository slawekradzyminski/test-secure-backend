package com.awesome.testing.controller.users;

import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserRefreshController {

    private final UserService userService;

    @GetMapping("/refresh")
    @Operation(summary = "Refresh JWT token", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "New JWT token",
                    content = @Content(schema = @Schema(type = "string", example = "eyJhbGciOiJIUzI1NiJ9..."))),
            @ApiResponse(responseCode = "401", description = "Access denied", content = @Content)
    })
    public String refresh(HttpServletRequest req) {
        return userService.refresh(userService.whoAmI(req).getUsername());
    }

}
