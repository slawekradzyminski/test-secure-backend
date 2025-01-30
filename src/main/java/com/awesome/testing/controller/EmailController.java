package com.awesome.testing.controller;

import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/email")
@Tag(name = "email", description = "Email sending endpoints")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @Value("${activemq.destination}")
    private String destination;

    @PostMapping
    @Operation(summary = "Send email", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email sent"),
            @ApiResponse(responseCode = "403", description = "Access denied")
    })
    public ResponseEntity<Void> sendEmail(@RequestBody EmailDTO emailDTO) {
        emailService.sendEmail(emailDTO, destination);
        return ResponseEntity.ok().build();
    }
}
