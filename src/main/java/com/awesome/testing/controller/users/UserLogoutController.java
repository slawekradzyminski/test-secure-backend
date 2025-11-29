package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.LogoutRequestDto;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserLogoutController {

    private final UserService userService;

    @PostMapping("/logout")
    @Operation(summary = "Logout user and revoke refresh token", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully logged out"),
            @ApiResponse(responseCode = "401", description = "Unauthorized", content = @Content)
    })
    public void logout(@Valid @RequestBody LogoutRequestDto request, HttpServletRequest httpRequest) {
        String username = userService.whoAmI(httpRequest).getUsername();
        userService.logout(request.getRefreshToken(), username);
    }
}
