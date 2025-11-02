package com.awesome.testing.controller.users;

import com.awesome.testing.dto.systemprompt.SystemPromptDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@CrossOrigin(origins = "http://localhost:8081", maxAge = 3600)
@RestController
@RequestMapping("/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
public class UserSystemPromptController {

    private final UserService userService;

    @GetMapping("/system-prompt")
    @Operation(summary = "Get your system prompt", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "System prompt retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token", content = @Content)
    })
    public SystemPromptDto getSystemPrompt(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        String username = principal.getUsername();
        String systemPrompt = userService.getSystemPrompt(username);
        return SystemPromptDto.builder()
                .systemPrompt(systemPrompt)
                .build();
    }

    @PutMapping("/system-prompt")
    @Operation(summary = "Update your system prompt", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "System prompt was updated"),
            @ApiResponse(responseCode = "401", description = "Unauthorized - Missing or invalid token", content = @Content)
    })
    public SystemPromptDto updateSystemPrompt(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "System prompt") @Valid @RequestBody SystemPromptDto systemPromptDto) {
        String username = principal.getUsername();
        UserEntity updatedUser = userService.updateSystemPrompt(username, systemPromptDto.getSystemPrompt());
        return SystemPromptDto.builder()
                .systemPrompt(updatedUser.getSystemPrompt())
                .build();
    }
} 
