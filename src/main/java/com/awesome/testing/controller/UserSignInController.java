package com.awesome.testing.controller;

import com.awesome.testing.dto.LoginDTO;
import com.awesome.testing.dto.LoginResponseDTO;
import com.awesome.testing.model.UserEntity;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserSignInController {

    private final UserService userService;

    @PostMapping("/signin")
    @Operation(summary = "${UserController.signin}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "Field validation failed"),
            @ApiResponse(responseCode = "422", description = "Invalid username/password supplied"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public LoginResponseDTO login(
            @Parameter(description = "Login details") @Valid @RequestBody LoginDTO loginDetails) {
        String token = userService.signIn(loginDetails);
        UserEntity userEntity = userService.search(loginDetails.getUsername());

        return LoginResponseDTO.builder()
                .username(userEntity.getUsername())
                .firstName(userEntity.getFirstName())
                .lastName(userEntity.getLastName())
                .roles(userEntity.getRoles())
                .token(token)
                .email(userEntity.getEmail())
                .build();
    }

}