package com.awesome.testing.controller.users;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserGetController {

    private final UserService userService;

    @GetMapping
    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Get all users")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "403", description = "Expired or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public List<UserResponseDto> search() {
        return userService.getAll();
    }

    @GetMapping(value = "/{username}")
    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Returns specific user by username")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "403", description = "Expired or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public UserResponseDto search(@Parameter(description = "Username") @PathVariable String username) {
        return UserResponseDto.from(userService.search(username));
    }

}