package com.awesome.testing.controller;

import com.awesome.testing.dto.gpt2.Gpt2InspectorStatusDto;
import com.awesome.testing.dto.gpt2.Gpt2EmbeddingSpaceRequestDto;
import com.awesome.testing.dto.gpt2.Gpt2TraceRequestDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.gpt2.Gpt2InspectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/learning/gpt2")
@Tag(name = "gpt2-inspector", description = "Full-local GPT-2 activation inspector")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized")
public class Gpt2InspectorController {

    private final Gpt2InspectorService inspectorService;
    private final AuthRateLimitGuard authRateLimitGuard;

    @Operation(
            summary = "Discover the optional full-local GPT-2 inspector",
            description = "Reports whether the private GPT-2 activation sidecar is configured, loaded, and ready."
    )
    @ApiResponse(responseCode = "200", description = "Inspector availability returned successfully")
    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Gpt2InspectorStatusDto status() {
        return inspectorService.status();
    }

    @Operation(
            summary = "Capture exact activations from GPT-2 small",
            description = "Runs the pinned local GPT-2 model and returns exact selected-head attention and residual tensors."
    )
    @ApiResponse(responseCode = "200", description = "Activation trace captured successfully")
    @ApiResponse(responseCode = "503", description = "The full-local inspector is not enabled")
    @PostMapping(value = "/trace", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode trace(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody Gpt2TraceRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        return inspectorService.trace(request);
    }

    @Operation(
            summary = "Explore the complete GPT-2 embedding table",
            description = "Finds a token across all GPT-2 vocabulary rows and returns a readable local 3D projection of its nearest embedding neighbors."
    )
    @ApiResponse(responseCode = "200", description = "Focused embedding neighborhood returned successfully")
    @ApiResponse(responseCode = "503", description = "The full-local inspector is not enabled")
    @PostMapping(value = "/embedding-space", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode embeddingSpace(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal CustomPrincipal principal,
            @Valid @RequestBody Gpt2EmbeddingSpaceRequestDto request) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        return inspectorService.embeddingSpace(request);
    }

    @Operation(
            summary = "Project the complete GPT-2 embedding table",
            description = "Returns a cached global 3D PCA projection for all 50,257 GPT-2 input embedding rows."
    )
    @ApiResponse(responseCode = "200", description = "Complete embedding projection returned successfully")
    @ApiResponse(responseCode = "503", description = "The full-local inspector is not enabled")
    @GetMapping(value = "/embedding-forest", produces = MediaType.APPLICATION_JSON_VALUE)
    public JsonNode embeddingForest(
            HttpServletRequest servletRequest,
            @AuthenticationPrincipal CustomPrincipal principal) {
        authRateLimitGuard.checkOllama(servletRequest, principal != null ? principal.getUsername() : null);
        return inspectorService.embeddingForest();
    }
}
