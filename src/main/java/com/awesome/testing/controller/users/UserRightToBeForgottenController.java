package com.awesome.testing.controller.users;

import com.awesome.testing.controller.doc.ForbiddenApiResponse;
import com.awesome.testing.controller.doc.UnauthorizedApiResponse;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserRightToBeForgottenController {

    private final UserService userService;

    @DeleteMapping("/{username}/right-to-be-forgotten")
    @PreAuthorize("@userService.exists(#username) and (hasRole('ROLE_ADMIN') or #username == authentication.principal.username)")
    @Operation(summary = "Delete user account and all user-owned data",
            security = @SecurityRequirement(name = "bearerAuth"))
    @UnauthorizedApiResponse
    @ForbiddenApiResponse
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "User account and user-owned data were deleted"),
            @ApiResponse(responseCode = "404", description = "The user doesn't exist", content = @Content)
    })
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forget(@Parameter(description = "Username") @PathVariable String username) {
        userService.forget(username);
    }
}
