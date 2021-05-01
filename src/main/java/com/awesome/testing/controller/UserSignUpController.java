package com.awesome.testing.controller;

import com.awesome.testing.dto.UserRegisterDTO;
import com.awesome.testing.dto.UserRegisterResponseDTO;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Api(tags = "users")
@RequiredArgsConstructor
public class UserSignUpController {

    private final UserService userService;
    private final ModelMapper modelMapper;

    @PostMapping("/signup")
    @ApiOperation(value = "${UserController.signup}")
    @ResponseStatus(HttpStatus.CREATED)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Field validation failed"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 422, message = "Username is already in use"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public UserRegisterResponseDTO signup(@ApiParam("Signup user") @Valid @RequestBody UserRegisterDTO user) {
        return userService.signUp(modelMapper.map(user, User.class));
    }

}
