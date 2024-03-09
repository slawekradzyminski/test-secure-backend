package com.awesome.testing.controller.users;

import com.awesome.testing.dto.users.LoginDto;
import com.awesome.testing.dto.users.LoginResponseDto;
import com.awesome.testing.security.JwtTokenUtil;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import static com.awesome.testing.util.TokenCookieUtil.buildTokenCookie;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserSignInController {

    private final UserService userService;
    private final JwtTokenUtil jwtTokenUtil;

    @PostMapping("/signin")
    @Operation(summary = "Authenticates user and returns its JWT token")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "400", description = "Field validation failed"),
            @ApiResponse(responseCode = "422", description = "Invalid username/password supplied"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public ResponseEntity<LoginResponseDto> login(
            @Parameter(description = "Login details") @Valid @RequestBody LoginDto loginDetails,
            HttpServletResponse response) {
        LoginResponseDto loginResponseDto = userService.signIn(loginDetails);
        response.addHeader("Set-Cookie",
                buildTokenCookie(loginResponseDto.getToken(), jwtTokenUtil.getTokenValidityInSeconds()));
        return ResponseEntity.ok(loginResponseDto);
    }

}