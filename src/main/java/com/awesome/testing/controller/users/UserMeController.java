package com.awesome.testing.controller.users;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserMeController {

    private final UserService userService;

    @GetMapping(value = "/me")
    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Returns current user's data")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "403", description = "Expired or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public UserResponseDto whoAmI(HttpServletRequest req) {
        return UserResponseDto.from(userService.whoAmI(req));
    }

}