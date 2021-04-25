package com.awesome.testing.controller;

import com.awesome.testing.dto.LoginDTO;
import com.awesome.testing.dto.LoginResponseDTO;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Api(tags = "users")
@RequiredArgsConstructor
public class UserSignInController {

    private final UserService userService;
    private final ModelMapper modelMapper;

    @PostMapping("/signin")
    @ApiOperation(value = "${UserController.signin}", response = LoginResponseDTO.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Field validation failed"),
            @ApiResponse(code = 422, message = "Invalid username/password supplied"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public LoginResponseDTO login(
            @ApiParam("Login details") @Valid @RequestBody LoginDTO loginDetails) {
        String token = userService.signIn(modelMapper.map(loginDetails, LoginDTO.class));
        User user = userService.search(loginDetails.getUsername());

        return LoginResponseDTO.builder()
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .token(token)
                .email(user.getEmail())
                .build();
    }

}
