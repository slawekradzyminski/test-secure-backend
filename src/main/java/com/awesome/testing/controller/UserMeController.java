package com.awesome.testing.controller;

import com.awesome.testing.dto.UserResponseDTO;
import com.awesome.testing.service.UserService;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@CrossOrigin(origins = "http://localhost:8082", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Api(tags = "users")
@RequiredArgsConstructor
public class UserMeController {

    private final UserService userService;
    private final ModelMapper modelMapper;

    @GetMapping(value = "/me")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_CLIENT')")
    @ApiOperation(value = "${UserController.me}", response = UserResponseDTO.class,
            authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public UserResponseDTO whoAmI(HttpServletRequest req) {
        return modelMapper.map(userService.whoAmI(req), UserResponseDTO.class);
    }

}
