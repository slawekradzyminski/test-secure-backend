package com.awesome.testing.controller;

import com.awesome.testing.service.SjpService;
import io.swagger.annotations.*;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = "http://localhost:8080", maxAge = 3600)
@RestController
@RequestMapping("/sjp")
@Api(tags = "sjp")
@RequiredArgsConstructor
public class SjpController {

    private final SjpService sjpService;
    private final ModelMapper modelMapper;

    @GetMapping(value = "/{length}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @ApiOperation(value = "${SjpController.getWords()}", authorizations = {@Authorization(value = "apiKey")})
    @ApiResponses(value = {
            @ApiResponse(code = 403, message = "Expired or invalid JWT token"),
            @ApiResponse(code = 403, message = "Access denied"),
            @ApiResponse(code = 404, message = "The user doesn't exist"),
            @ApiResponse(code = 500, message = "Something went wrong")
    })
    public List<String> getWords(@ApiParam("length") @PathVariable String length) {
        return sjpService.getWords(Integer.parseInt(length));
    }

}
