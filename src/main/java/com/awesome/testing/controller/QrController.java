package com.awesome.testing.controller;

import com.awesome.testing.dto.ValidationErrorsDto;
import com.awesome.testing.dto.ErrorDto;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Content;
import com.awesome.testing.dto.qr.CreateQrDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.security.ratelimit.AuthRateLimitGuard;
import com.awesome.testing.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/qr")
@Tag(name = "QR", description = "Endpoint for QR code generation")
@RequiredArgsConstructor
public class QrController {

    private final AuthRateLimitGuard authRateLimitGuard;
    private final QrService qrService;

    // application/json is listed so a request that only accepts JSON still reaches validation and gets its 400
    // field errors; a successful response is always image/png and is refused with 406 when Accept excludes it.
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = {MediaType.IMAGE_PNG_VALUE, MediaType.APPLICATION_JSON_VALUE})
    @Operation(summary = "Generate QR code",
            description = "Generates a PNG QR code for the supplied text payload. Non-ASCII text (Polish letters, emoji) is encoded "
                    + "as UTF-8 and decodes to exactly the submitted text. The capacity is counted in UTF-8 bytes: about 2953 "
                    + "bytes, so fewer characters for multi-byte text; longer text answers 400 {\"text\": \"Text is too long for a QR code\"}.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Successfully generated QR code", content = @Content(mediaType = "image/png", schema = @Schema(type = "string", format = "binary")))
    @ApiResponse(responseCode = "400", description = "Invalid input: blank text, or text too long for a QR code",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "406", description = "The Accept header excludes image/png (validation errors are still "
            + "reported as 400 JSON first)",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "429", description = "Too many requests",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<byte[]> createQrCode(HttpServletRequest request,
                                               @AuthenticationPrincipal CustomPrincipal principal,
                                               @Valid @RequestBody CreateQrDto createQrDto)
            throws HttpMediaTypeNotAcceptableException {
        authRateLimitGuard.checkQr(request, principal != null ? principal.getUsername() : null);
        if (!acceptsPng(request.getHeader(HttpHeaders.ACCEPT))) {
            throw new HttpMediaTypeNotAcceptableException(List.of(MediaType.IMAGE_PNG));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(qrService.generateQrPng(createQrDto.getText()));
    }

    private static boolean acceptsPng(String accept) {
        if (accept == null || accept.isBlank()) {
            return true;
        }
        try {
            return MediaType.parseMediaTypes(accept).stream()
                    .anyMatch(type -> type.isCompatibleWith(MediaType.IMAGE_PNG) && type.getQualityValue() > 0);
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

}
