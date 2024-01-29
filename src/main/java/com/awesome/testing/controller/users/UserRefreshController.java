package com.awesome.testing.controller.users;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.dto.users.LoginResponseDto;
import com.awesome.testing.security.JwtTokenUtil;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import static com.awesome.testing.util.TokenCookieUtil.buildTokenCookie;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserRefreshController {

    private final UserService userService;
    private final JwtTokenUtil jwtTokenUtil;

    @PreAuthorizeForAllRoles
    @GetMapping("/refresh")
    @OperationWithSecurity(summary = "Refreshes token for logged in user")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirect to login page"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public ResponseEntity<?> refresh(HttpServletRequest req, HttpServletResponse response) {
        String remoteUser = req.getRemoteUser();
        String token = userService.refreshToken(remoteUser);
        response.addHeader("Set-Cookie", buildTokenCookie(token, jwtTokenUtil.getTokenValidityInSeconds()));
        return ResponseEntity.ok(LoginResponseDto.from(userService.search(remoteUser), token));
    }

}