package com.awesome.testing.controller;

import com.awesome.testing.dto.UserEditDTO;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Api(tags = "users")
@RequiredArgsConstructor
public class UserEditController {

    private final UserService userService;

    @PutMapping(value = "/{username}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ApiOperation(value = "${UserController.edit}",
            authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "The user doesn't exist"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public void edit(@ApiParam("Username") @PathVariable String username,
                        @ApiParam("User details") @Valid @RequestBody UserEditDTO userEditBody) {
        User user = userService.search(username);
        user.setFirstName(userEditBody.getFirstName());
        user.setLastName(userEditBody.getLastName());
        user.setEmail(userEditBody.getEmail());
        user.setRoles(userEditBody.getRoles());
        userService.save(user);
    }

}
