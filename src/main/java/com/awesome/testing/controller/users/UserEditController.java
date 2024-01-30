package com.awesome.testing.controller.users;

import com.awesome.testing.controller.utils.authorization.OperationWithSecurity;
import com.awesome.testing.controller.utils.authorization.PreAuthorizeForAllRoles;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserEditDto;
import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.exception.ApiException;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/users")
@Tag(name = "users")
@RequiredArgsConstructor
public class UserEditController {

    private final UserService userService;

    @PutMapping(value = "/{username}")
    @PreAuthorizeForAllRoles
    @OperationWithSecurity(summary = "Edits user details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "403", description = "Expired or invalid JWT token"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist"),
            @ApiResponse(responseCode = "500", description = "Something went wrong")
    })
    public void edit(@Parameter(description = "Username") @PathVariable String username,
                     @Parameter(description = "User details") @Valid @RequestBody UserEditDto userEditBody,
                     HttpServletRequest req) {
        String currentUsername = req.getRemoteUser();
        UserEntity currentUser = userService.search(currentUsername);

        if (!currentUser.getRoles().contains(Role.ROLE_ADMIN) && !currentUsername.equals(username)) {
            throw new ApiException("You can only edit your own details", HttpStatus.FORBIDDEN);
        }

        userService.edit(username, userEditBody);
    }

}