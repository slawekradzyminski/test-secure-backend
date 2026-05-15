package com.awesome.testing.controller;

import com.awesome.testing.dto.email.EmailDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.repository.UserRepository;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/email")
@Tag(name = "email", description = "Email sending endpoints")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized")
public class EmailController {

    private final AuthRateLimitGuard authRateLimitGuard;
    private final EmailService emailService;
    private final UserRepository userRepository;

    @Value("${activemq.destination}")
    private String destination;

    @PostMapping
    @Operation(summary = "Send email",
            description = "Queues an email message for asynchronous delivery through the configured JMS destination.")
    @ApiResponse(responseCode = "200", description = "Email sent successfully")
    @ApiResponse(responseCode = "400", description = "Invalid email data")
    @ApiResponse(responseCode = "429", description = "Too many requests")
    public ResponseEntity<Void> sendEmail(HttpServletRequest request,
                                          @AuthenticationPrincipal CustomPrincipal principal,
                                          @RequestBody @Valid EmailDto emailDto) {
        authRateLimitGuard.checkEmail(request, principal != null ? principal.getUsername() : null);
        UserEntity user = principal == null ? null : userRepository.findByUsername(principal.getUsername()).orElse(null);
        emailService.sendEmail(emailDto, destination, user);
        return ResponseEntity.ok().build();
    }
}
