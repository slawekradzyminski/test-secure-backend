package com.awesome.testing.controller.users;

import com.awesome.testing.dto.users.LoginDTO;
import com.awesome.testing.dto.users.LoginResponseDTO;
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

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
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
    public ResponseEntity<LoginResponseDTO> login(
            @Parameter(description = "Login details") @Valid @RequestBody LoginDTO loginDetails,
            HttpServletResponse response) {
        LoginResponseDTO loginResponseDTO = userService.signIn(loginDetails);
        String cookie = "token=" + loginResponseDTO.getToken() + 
        "; Max-Age=3600; Path=/; HttpOnly; SameSite=None; Secure";
        response.addHeader("Set-Cookie", cookie);
        return ResponseEntity.ok(loginResponseDTO);
    }

}