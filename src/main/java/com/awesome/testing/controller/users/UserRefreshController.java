package com.awesome.testing.controller.users;

import com.awesome.testing.dto.user.RefreshTokenRequestDto;
import com.awesome.testing.dto.user.TokenPair;
import com.awesome.testing.dto.user.TokenRefreshResponseDto;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
public class UserRefreshController {

    private final UserService userService;

    @PostMapping("/refresh")
    @Operation(summary = "Refresh JWT token using refresh token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "New JWT and refresh tokens",
                    content = @Content(schema = @Schema(implementation = TokenRefreshResponseDto.class))),
            @ApiResponse(responseCode = "401", description = "Invalid refresh token", content = @Content)
    })
    public TokenRefreshResponseDto refresh(@Valid @RequestBody RefreshTokenRequestDto request) {
        TokenPair tokens = userService.refresh(request.getRefreshToken());
        return TokenRefreshResponseDto.from(tokens);
    }

}
