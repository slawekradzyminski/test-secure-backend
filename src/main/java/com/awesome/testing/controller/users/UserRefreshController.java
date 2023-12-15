package com.awesome.testing.controller.users;

import com.awesome.testing.dto.users.LoginResponseDTO;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserRefreshController {

    private final UserService userService;

    @GetMapping("/refresh")
    @Operation(summary = "${UserController.refresh}",
            security = {@SecurityRequirement(name = "Authorization")})
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirect to login page"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse response) {
        String remoteUser = req.getRemoteUser();
        String token = userService.refreshToken(remoteUser);
        String cookie = "token=" + token +
                "; Max-Age=3600; Path=/; HttpOnly; SameSite=None; Secure";
        response.addHeader("Set-Cookie", cookie);
        return ResponseEntity.ok(LoginResponseDTO.from(userService.search(remoteUser), token));
    }

}