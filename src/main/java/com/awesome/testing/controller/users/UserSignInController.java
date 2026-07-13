package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.LoginDto;
import com.awesome.testing.dto.user.LoginResponseDto;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserSignInController {

    private final AuthRateLimitGuard authRateLimitGuard;
    private final UserService userService;

    @PostMapping("/signin")
    @Operation(summary = "Authenticate user and return JWT token",
            description = "Authenticates local credentials and returns a JWT access token with a refresh token.")
    @ApiResponse(responseCode = "200", description = "Successfully authenticated")
    @ApiResponse(responseCode = "400", description = "Field validation failed")
    @ApiResponse(responseCode = "422", description = "Invalid username/password supplied")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public LoginResponseDto login(
            HttpServletRequest request,
            @Parameter(description = "Login details") @Valid @RequestBody LoginDto loginDetails) {
        authRateLimitGuard.checkSignIn(request, loginDetails.getUsername());
        return userService.signIn(loginDetails.getUsername(), loginDetails.getPassword());
    }

}
