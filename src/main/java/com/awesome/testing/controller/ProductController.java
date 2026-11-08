package com.awesome.testing.controller;

import com.awesome.testing.dto.product.ProductCreateDto;
import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.product.ProductUpdateDto;
import com.awesome.testing.service.ProductService;
import com.awesome.testing.service.VersionedProduct;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import org.springframework.http.HttpHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import com.awesome.testing.dto.ErrorDto;
import com.awesome.testing.dto.ValidationErrorsDto;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product management endpoints")
@SecurityRequirement(name = "bearerAuth")
@ApiResponse(responseCode = "401", description = "Unauthorized",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @Operation(summary = "Get all products",
            description = "Returns the complete product catalog visible to authenticated clients.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved products")
    public ResponseEntity<List<ProductDto>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID",
            description = "Returns one product by numeric identifier, or 404 when the product does not exist.")
    @ApiResponse(responseCode = "200", description = "Successfully retrieved product; the ETag header carries the product version",
            headers = @Header(name = HttpHeaders.ETAG, description = "Quoted product version", schema = @Schema(type = "string", example = "\"3\"")))
    @ApiResponse(responseCode = "304", description = "If-None-Match matches the current version; empty response body", content = @Content)
    @ApiResponse(responseCode = "400", description = "Bad request",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ProductDto> getProductById(
            @PathVariable Long id,
            @Parameter(description = "Entity tag from a previous response; a match returns 304")
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        VersionedProduct product = productService.getVersionedProduct(id);
        if (EntityTags.matches(ifNoneMatch, product.version())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(product.etag()).build();
        }
        return ResponseEntity.ok().eTag(product.etag()).body(product.product());
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Create new product",
            description = "Creates a catalog product from the supplied payload. Requires an administrator role.")
    @ApiResponse(responseCode = "201", description = "Product created successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ProductDto> createProduct(@Valid @RequestBody ProductCreateDto productCreateDto) {
        ProductDto savedProduct = productService.createProduct(productCreateDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(savedProduct);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Update existing product",
            description = "Updates supplied non-null product fields; omitted or null fields are unchanged. Empty description and imageUrl are allowed. An optional If-Match header with the ETag from GET enables optimistic concurrency: a stale tag returns 412 and nothing changes. Requires an administrator role.")
    @ApiResponse(responseCode = "200", description = "Product updated successfully; the ETag header carries the new version",
            headers = @Header(name = HttpHeaders.ETAG, description = "Quoted product version", schema = @Schema(type = "string", example = "\"4\"")))
    @ApiResponse(responseCode = "412", description = "If-Match does not match the current product version",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "400", description = "Invalid input",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Product not found",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    public ResponseEntity<ProductDto> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateDto productUpdateDto,
            @Parameter(description = "Entity tag from GET /api/v1/products/{id}, for example \"3\"")
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        VersionedProduct product = productService.updateProduct(id, productUpdateDto, EntityTags.parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(product.etag()).body(product.product());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Operation(summary = "Delete product",
            description = "Deletes an existing product by identifier. Referenced products return 409 without removing existing data. Requires an administrator role.")
    @ApiResponse(responseCode = "204", description = "Product deleted successfully", content = @Content)
    @ApiResponse(responseCode = "400", description = "Bad request",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsDto.class)))
    @ApiResponse(responseCode = "403", description = "Forbidden",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "409", description = "Product is referenced by existing data",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDto.class)))
    @ApiResponse(responseCode = "404", description = "Product not found; empty response body", content = @Content)
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        return productService.deleteProduct(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
} 
