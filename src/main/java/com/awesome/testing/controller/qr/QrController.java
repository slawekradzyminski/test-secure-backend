package com.awesome.testing.controller.qr;

import com.awesome.testing.dto.qr.CreateQrDto;
import com.awesome.testing.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.awt.image.BufferedImage;

@CrossOrigin(origins = {"http://localhost:8081", "http://127.0.0.1:8081"}, maxAge = 36000, allowCredentials = "true")
@RestController
@RequestMapping("/qr")
@Tag(name = "qr")
@RequiredArgsConstructor
public class QrController {

    private final QrService qrService;

//    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_CLIENT')")
    @Operation(summary = "Generate QR Code")
//            security = {@SecurityRequirement(name = "Authorization")})
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.IMAGE_PNG_VALUE)
    public BufferedImage createQrCode(@RequestBody @Validated CreateQrDto createQrDto)
            throws Exception {
        return qrService.generateQrCode(createQrDto.getText());
    }

}
