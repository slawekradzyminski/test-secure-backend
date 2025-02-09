package com.awesome.testing.controller.users;

import com.awesome.testing.dto.LoginDto;
import com.awesome.testing.dto.LoginResponseDto;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserSignInController {

    private final UserService userService;

    @PostMapping("/signin")
    @Operation(summary = "Authenticate user and return JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully authenticated",
                    content = @Content(schema = @Schema(implementation = LoginResponseDto.class))),
            @ApiResponse(responseCode = "400", description = "Field validation failed", content = @Content),
            @ApiResponse(responseCode = "422", description = "Invalid username/password supplied", content = @Content)
    })
    public LoginResponseDto login(
            @Parameter(description = "Login details") @Valid @RequestBody LoginDto loginDetails) {
        String token = userService.signIn(loginDetails.getUsername(), loginDetails.getPassword());
        User user = userService.search(loginDetails.getUsername());

        return LoginResponseDto.from(token, user);
    }

}
