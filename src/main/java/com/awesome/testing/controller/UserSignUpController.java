package com.awesome.testing.controller;

import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.dto.UserRegisterResponseDTO;
import com.awesome.testing.model.UserEntity;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserSignUpController {

    private final UserService userService;

    @PostMapping("/signup")
    @Operation(summary = "${UserController.signup}")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "Field validation failed"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "422", description = "Username is already in use"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public UserRegisterResponseDTO signup(
            @Parameter(description = "Signup user") @Valid @RequestBody UserRegisterDTO user) {
        return userService.signUp(UserEntity.from(user));
    }

}