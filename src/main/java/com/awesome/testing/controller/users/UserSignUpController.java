package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
@Validated
public class UserSignUpController {

    private final AuthRateLimitGuard authRateLimitGuard;
    private final UserService userService;

    @PostMapping("/signup")
    @Operation(summary = "Create a new user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User was successfully created"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "429", description = "Too many requests", content = @Content)
    })
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(HttpServletRequest request,
                       @Parameter(description = "Signup User") @Valid @RequestBody UserRegisterDto userDto) {
        authRateLimitGuard.checkSignUp(request);
        userService.signup(userDto);
    }

}
