package com.awesome.testing.controller.users;

import com.awesome.testing.controller.doc.UnauthorizedApiResponse;
import com.awesome.testing.dto.email.EmailEventDto;
import com.awesome.testing.entity.UserEntity;
import com.awesome.testing.service.UserService;
import com.awesome.testing.service.email.EmailEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/email-events")
@Tag(name = "email-events", description = "Authenticated email event visibility")
@RequiredArgsConstructor
public class UserEmailEventController {

    private final UserService userService;
    private final EmailEventService emailEventService;

    @GetMapping
    @Operation(
            summary = "Get recent email events for the current user",
            description = "Returns the latest email events recorded for the authenticated user. "
                    + "Statuses reflect handoff to the Mailhog-backed test sink, not real-world inbox delivery.",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @UnauthorizedApiResponse
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recent email events returned successfully",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = EmailEventDto.class))))
    })
    public List<EmailEventDto> getMyEmailEvents(HttpServletRequest request) {
        UserEntity user = userService.whoAmI(request);
        return emailEventService.getLatestEventsFor(user);
    }
}
