package com.awesome.testing.controller;

import com.awesome.testing.controller.doc.UnauthorizedApiResponse;
import com.awesome.testing.dto.qr.CreateQrDto;
import com.awesome.testing.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

@RestController
@RequestMapping("/api/v1/qr")
@Tag(name = "QR", description = "Endpoint for QR code generation")
@RequiredArgsConstructor
public class QrController {

    private final QrService qrService;

    @SneakyThrows
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Generate QR code", security = @SecurityRequirement(name = "bearerAuth"))
    @UnauthorizedApiResponse
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully generated QR code"),
            @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    })
    public byte[] createQrCode(@RequestBody @Validated CreateQrDto createQrDto) {
        BufferedImage qrImage = qrService.generateQrCode(createQrDto.getText());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "png", baos);
        return baos.toByteArray();
    }

}
