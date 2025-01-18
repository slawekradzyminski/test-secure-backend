package com.awesome.testing.controller;

import com.awesome.testing.dto.EmailDTO;
import com.awesome.testing.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/email")
@Tag(name = "email", description = "Email sending endpoints")
@RequiredArgsConstructor
public class EmailController {

    private final EmailService emailService;

    @PostMapping
    @Operation(summary = "Send an email")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Email sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data", content = @Content),
            @ApiResponse(responseCode = "500", description = "Error sending email", content = @Content)
    })
    public ResponseEntity<Void> sendEmail(
            @Parameter(description = "Email details") @Valid @RequestBody EmailDTO emailDTO) {
        emailService.sendEmail(emailDTO);
        return ResponseEntity.ok().build();
    }

}
