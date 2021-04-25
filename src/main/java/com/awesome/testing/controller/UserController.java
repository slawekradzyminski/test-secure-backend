package com.awesome.testing.controller;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import com.awesome.testing.dto.LoginDto;
import com.awesome.testing.dto.LoginResponseDto;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Authorization;
import com.awesome.testing.dto.UserDataDTO;
import com.awesome.testing.dto.UserResponseDTO;
import com.awesome.testing.model.User;
import com.awesome.testing.service.UserService;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/users")
@Api(tags = "users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final ModelMapper modelMapper;

    @PostMapping("/signin")
    @ApiOperation(value = "${UserController.signin}", response = LoginResponseDto.class)
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Field validation failed"),
            @ApiResponse(code = 422, message = "Invalid username/password supplied"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public LoginResponseDto login(
            @ApiParam("Login details") @Valid @RequestBody LoginDto loginDetails) {
        String token = userService.signIn(modelMapper.map(loginDetails, LoginDto.class));
        User user = userService.search(loginDetails.getUsername());

        return LoginResponseDto.builder()
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .roles(user.getRoles())
                .token(token)
                .build();
    }

    @PostMapping("/signup")
    @ApiOperation(value = "${UserController.signup}")
    @ApiResponses(value = {
            @ApiResponse(code = 400, message = "Field validation failed"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 422, message = "Username is already in use"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public String signup(@ApiParam("Signup user") @Valid @RequestBody UserDataDTO user) {
        return userService.signUp(modelMapper.map(user, User.class));
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_CLIENT')")
    @ApiOperation(value = "${UserController.getAll}", response = UserResponseDTO[].class,
            authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "The user doesn't exist"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public List<UserResponseDTO> search() {
        return userService.getAll()
                .stream()
                .map(user -> modelMapper.map(user, UserResponseDTO.class))
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/{username}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ApiOperation(value = "${UserController.search}", response = UserResponseDTO.class,
            authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "The user doesn't exist"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public UserResponseDTO search(@ApiParam("Username") @PathVariable String username) {
        return modelMapper.map(userService.search(username), UserResponseDTO.class);
    }

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

    @DeleteMapping(value = "/{username}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ApiOperation(value = "${UserController.delete}",
            authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "The user doesn't exist"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public String delete(@ApiParam("Username") @PathVariable String username) {
        userService.delete(username);
        return username;
    }

    @GetMapping("/refresh")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_CLIENT')")
    @ApiOperation(value = "${UserController.refresh}",
            authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public String refresh(HttpServletRequest req) {
        return userService.refresh(req.getRemoteUser());
    }

}
