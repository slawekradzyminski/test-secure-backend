package com.awesome.testing.controller.catalog;

import com.awesome.testing.controller.exception.CustomException;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.catalog.ImportReportDto;
import com.awesome.testing.security.CustomPrincipal;
import com.awesome.testing.service.catalog.ProductCsvService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ROLE_ADMIN')")
@RequestMapping("/api/v1/admin/products")
@Tag(name = "Products", description = "Product management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
@ApiResponse(responseCode = "403", description = "Forbidden",
        content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class AdminCatalogController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ProductCsvService csvService;
    private final Clock commerceClock;

    @GetMapping(value = "/export", produces = "text/csv")
    @Operation(summary = "Export products as CSV", description = "Downloads every product as RFC 4180 CSV with header id,name,description,price,stockQuantity,category,imageUrl. Text cells that start with =, +, -, @, tab or carriage return are prefixed with a single quote.")
    @ApiResponse(responseCode = "200", description = "CSV file",
            content = @Content(mediaType = "text/csv", schema = @Schema(type = "string")))
    public ResponseEntity<String> export() {
        String filename = "products-" + LocalDate.now(commerceClock).format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(csvService.export());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Import products from CSV", description = "Creates products for rows without an id and updates products for rows with an existing id. Columns may appear in any order; name, description, price, stockQuantity and category are required. Files are limited to 1 MB and 1000 rows. The import is all-or-nothing: any row error returns 400 with the full report and persists nothing. dryRun=true validates without persisting.")
    @ApiResponse(responseCode = "200", description = "Import applied, or dry run succeeded")
    @ApiResponse(responseCode = "400", description = "Invalid file or rows; the report lists every error when the file could be parsed",
            content = @Content(mediaType = "application/json", schema = @Schema(anyOf = {ImportReportDto.class, ErrorDto.class})))
    @ApiResponse(responseCode = "413", description = "File larger than 1 MB",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ImportReportDto> importCsv(
            @Parameter(hidden = true) @AuthenticationPrincipal CustomPrincipal principal,
            @Parameter(description = "CSV file") @RequestPart("file") MultipartFile file,
            @Parameter(description = "Validate without persisting") @RequestParam(defaultValue = "false") boolean dryRun) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new CustomException("Could not read the uploaded file", HttpStatus.BAD_REQUEST, e);
        }
        ImportReportDto report = csvService.importCsv(content, dryRun, principal.getUsername());
        return ResponseEntity.status(report.getErrors().isEmpty() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).body(report);
    }
}
