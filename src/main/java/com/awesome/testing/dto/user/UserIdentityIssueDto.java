package com.awesome.testing.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "An account that predates the username and email rules and should be cleaned up",
        requiredProperties = {"id", "username", "email", "issues"})
public record UserIdentityIssueDto(
        @Schema(description = "User id", example = "42") Integer id,
        @Schema(description = "Username as stored", example = "Client") String username,
        @Schema(description = "Email as stored", example = "Alice@Example.com") String email,
        @Schema(description = "Rules the account breaks") List<IdentityIssue> issues) {
}
