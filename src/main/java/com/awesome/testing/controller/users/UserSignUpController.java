package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.UserRegisterDto;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "Create a new user account",
            description = "Registers a local user account with client privileges after validating the signup payload.")
    @ApiResponse(responseCode = "201", description = "User was successfully created", content = @Content)
    @ApiResponse(responseCode = "400", description = "Field validation failed, or username/email is already in use",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ValidationErrorsDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "401", description = "Invalid or expired Bearer token; omit stale Authorization headers on this public endpoint",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(HttpServletRequest request,
                       @Parameter(description = "Signup User") @Valid @RequestBody UserRegisterDto userDto) {
        authRateLimitGuard.checkSignUp(request);
        userService.signup(userDto);
    }

}
