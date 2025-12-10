package com.awesome.testing.controller;

import com.awesome.testing.dto.conversation.ConversationDetailDto;
import com.awesome.testing.dto.conversation.ConversationSummaryDto;
import com.awesome.testing.dto.conversation.ConversationType;
import com.awesome.testing.dto.conversation.CreateConversationRequestDto;
import com.awesome.testing.dto.conversation.UpdateConversationRequestDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.ConversationHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ollama/conversations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "conversations", description = "Persistent LLM conversation endpoints")
public class ConversationController {

    private final ConversationHistoryService conversationHistoryService;

    @GetMapping
    @Operation(summary = "List your conversations")
    public List<ConversationSummaryDto> listConversations(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @RequestParam(value = "type", required = false) ConversationType type) {
        return conversationHistoryService.listConversations(principal.getUsername(), type);
    }

    @PostMapping
    @Operation(summary = "Create a new conversation")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversation created successfully",
                    content = @Content(schema = @Schema(implementation = ConversationDetailDto.class))),
            @ApiResponse(responseCode = "400", description = "Invalid payload", content = @Content)
    })
    public ConversationDetailDto createConversation(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody CreateConversationRequestDto request) {
        return conversationHistoryService.createConversation(principal.getUsername(), request);
    }

    @GetMapping("/{conversationId}")
    @Operation(summary = "Get conversation details")
    public ConversationDetailDto getConversation(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable UUID conversationId) {
        return conversationHistoryService.getConversation(principal.getUsername(), conversationId);
    }

    @PatchMapping("/{conversationId}")
    @Operation(summary = "Update conversation metadata")
    public ConversationSummaryDto updateConversation(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable UUID conversationId,
            @Valid @RequestBody UpdateConversationRequestDto request) {
        return conversationHistoryService.updateConversation(principal.getUsername(), conversationId, request);
    }

    @DeleteMapping("/{conversationId}")
    @Operation(summary = "Archive a conversation")
    public ResponseEntity<Void> archiveConversation(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @PathVariable UUID conversationId) {
        conversationHistoryService.archiveConversation(principal.getUsername(), conversationId);
        return ResponseEntity.noContent().build();
    }
}
