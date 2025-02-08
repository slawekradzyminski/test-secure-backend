package com.awesome.testing.controller;

import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.model.UserEntity;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
@Validated
public class UserSignUpController {

    private final UserService userService;
    private final ModelMapper modelMapper;

    @PostMapping("/signup")
    @Operation(summary = "Create a new user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User was successfully created"),
            @ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content)
    })
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Parameter(description = "Signup User") @Valid @RequestBody UserRegisterDto userDto) {
        try {
            UserEntity user = modelMapper.map(userDto, UserEntity.class);
            userService.signup(user);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid user data: " + e.getMessage());
        }
    }

}
