package com.awesome.testing.controller.users;

import com.awesome.testing.dto.systemprompt.ChatSystemPromptDto;
import com.awesome.testing.dto.systemprompt.ToolSystemPromptDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "users", description = "User management endpoints")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized")
public class UserPromptController {

    private final UserService userService;

    @GetMapping("/chat-system-prompt")
    @Operation(summary = "Get your chat system prompt",
            description = "Returns the authenticated user's chat prompt override, or the default prompt when no override is stored.")
    @ApiResponse(responseCode = "200", description = "Chat system prompt retrieved successfully")
    public ChatSystemPromptDto getChatSystemPrompt(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        String username = principal.getUsername();
        String systemPrompt = userService.getChatSystemPrompt(username);
        return ChatSystemPromptDto.builder()
                .chatSystemPrompt(systemPrompt)
                .build();
    }

    @PutMapping("/chat-system-prompt")
    @Operation(summary = "Update your chat system prompt",
            description = "Stores a new chat prompt override for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Chat system prompt was updated")
    @ApiResponse(responseCode = "400", description = "Bad request")
    public ChatSystemPromptDto updateChatSystemPrompt(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "Chat system prompt") @Valid @RequestBody ChatSystemPromptDto systemPromptDto) {
        String username = principal.getUsername();
        UserEntity updatedUser = userService.updateChatSystemPrompt(username, systemPromptDto.getChatSystemPrompt());
        return ChatSystemPromptDto.builder()
                .chatSystemPrompt(updatedUser.getChatSystemPrompt())
                .build();
    }

    @GetMapping("/tool-system-prompt")
    @Operation(summary = "Get your tool system prompt",
            description = "Returns the authenticated user's tool-calling prompt override, or the default prompt when no override is stored.")
    @ApiResponse(responseCode = "200", description = "Tool system prompt retrieved successfully")
    public ToolSystemPromptDto getToolSystemPrompt(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal) {
        String username = principal.getUsername();
        String systemPrompt = userService.getToolSystemPrompt(username);
        return ToolSystemPromptDto.builder()
                .toolSystemPrompt(systemPrompt)
                .build();
    }

    @PutMapping("/tool-system-prompt")
    @Operation(summary = "Update your tool system prompt",
            description = "Stores a new tool-calling prompt override for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Tool system prompt was updated")
    @ApiResponse(responseCode = "400", description = "Bad request")
    public ToolSystemPromptDto updateToolSystemPrompt(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "Tool system prompt") @Valid @RequestBody ToolSystemPromptDto systemPromptDto) {
        String username = principal.getUsername();
        UserEntity updatedUser = userService.updateToolSystemPrompt(username, systemPromptDto.getToolSystemPrompt());
        return ToolSystemPromptDto.builder()
                .toolSystemPrompt(updatedUser.getToolSystemPrompt())
                .build();
    }
}
