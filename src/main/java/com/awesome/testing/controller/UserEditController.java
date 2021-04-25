package com.awesome.testing.controller;

import com.awesome.testing.dto.UserResponseDTO;
import com.awesome.testing.service.UserService;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/users")
@Api(tags = "users")
@RequiredArgsConstructor
public class UserEditController {

    private final UserService userService;
    private final ModelMapper modelMapper;

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
    public String edit(@ApiParam("Username") @PathVariable String username,
                        @ApiParam("User details") @Valid @RequestBody UserResponseDTO userResponseDTO) {
        userService.delete(username);
        return username;
    }

}
