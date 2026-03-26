package com.awesome.testing.controller;

import com.awesome.testing.controller.doc.UnauthorizedApiResponse;
import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/email")
@Tag(name = "email", description = "Email sending endpoints")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@UnauthorizedApiResponse
public class EmailController {

    private final EmailService emailService;

    @Value("${activemq.destination}")
    private String destination;

    @PostMapping
    @Operation(summary = "Send email")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid email data", content = @Content)
    })
    public ResponseEntity<Void> sendEmail(@RequestBody @Valid EmailDto emailDto) {
        emailService.sendEmail(emailDto, destination);
        return ResponseEntity.ok().build();
    }
}
