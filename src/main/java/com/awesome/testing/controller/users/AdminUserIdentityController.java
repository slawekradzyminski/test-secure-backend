package com.awesome.testing.controller.users;

import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.user.UserIdentityIssueDto;
import com.awesome.testing.service.identity.UserIdentityReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/users")
@Tag(name = "users", description = "User management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserIdentityController {

    private final UserIdentityReport userIdentityReport;

    @GetMapping(value = "/identity-issues", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List accounts that break the username and email rules (Admin only)",
            description = "Accounts created before usernames became case-insensitive with a fixed character set "
                    + "and emails became case-insensitive keep working; this lists them for clean-up: usernames "
                    + "outside 4-255 letters, digits, '.', '_' or '-', case-variant duplicates, a username equal to "
                    + "another account's email, and blank, invalid or non-lower-case emails. Ordered by id.")
    @ApiResponse(responseCode = "200", description = "Accounts with at least one issue; empty when all comply",
            content = @Content(mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = UserIdentityIssueDto.class))))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public List<UserIdentityIssueDto> identityIssues() {
        return userIdentityReport.issues();
    }
}
