package com.awesome.testing.endpoints.commerce;

import com.awesome.testing.dto.catalog.ImportReportDto;
import com.awesome.testing.dto.catalog.ImportRowErrorDto;
import com.awesome.testing.entity.ProductEntity;
import com.awesome.testing.entity.inventory.InventoryMovementType;
import com.awesome.testing.repository.inventory.InventoryMovementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCsvFlowTest extends AbstractCommerceFlowTest {

    private static final String EXPORT = "/api/v1/admin/products/export";
    private static final String IMPORT = "/api/v1/admin/products/import";

    @Autowired
    private InventoryMovementRepository movementRepository;

    @Test
    void exportQuotesSpecialCharactersAndNeutralisesFormulas() {
        ProductEntity product = productRepository.save(ProductEntity.builder()
                .name("=HYPERLINK(\"http://evil\")")
                .description("Line one, with comma\nline two")
                .price(new java.math.BigDecimal("12.50"))
                .stockQuantity(3)
                .category("Tests")
                .build());

        HttpHeaders headers = getHeadersWith(adminToken);
        headers.setAccept(java.util.List.of(MediaType.parseMediaType("text/csv")));
        ResponseEntity<String> response = executeGet(EXPORT, headers, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType().isCompatibleWith(MediaType.parseMediaType("text/csv"))).isTrue();
        assertThat(response.getHeaders().getContentDisposition().getFilename()).matches("products-\\d{8}\\.csv");
        assertThat(response.getBody()).startsWith("id,name,description,price,stockQuantity,category,imageUrl\r\n");
        assertThat(response.getBody()).contains(product.getId()
                + ",\"'=HYPERLINK(\"\"http://evil\"\")\",\"Line one, with comma\nline two\",12.50,3,Tests,\r\n");
    }

    @Test
    void validImportCreatesAndUpdatesProductsAndRecordsStockMovements() {
        ProductEntity existing = product("10.00", 5);
        String csv = "name,description,price,stockQuantity,category,id\n"
                + "Imported lamp,Warm light,49.99,7,Home,\n"
                + "Renamed product,Updated text,11.00,9,Tests," + existing.getId() + "\n";

        ResponseEntity<ImportReportDto> response = upload(csv, false, ImportReportDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isApplied()).isTrue();
        assertThat(response.getBody().getCreated()).isEqualTo(1);
        assertThat(response.getBody().getUpdated()).isEqualTo(1);
        ProductEntity updated = productRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Renamed product");
        assertThat(updated.getStockQuantity()).isEqualTo(9);
        assertThat(movementRepository.findAll()).anySatisfy(movement -> {
            assertThat(movement.getType()).isEqualTo(InventoryMovementType.ADMIN_ADJUSTMENT);
            assertThat(movement.getReason()).isEqualTo("CSV import");
            assertThat(movement.getDelta()).isEqualTo(4);
        });
        assertThat(productRepository.findAll()).extracting(ProductEntity::getName).contains("Imported lamp");
    }

    @Test
    void anyInvalidRowRejectsTheWholeFileAndReportsEveryError() {
        ProductEntity existing = product("10.00", 5);
        String csv = "id,name,description,price,stockQuantity,category,imageUrl\n"
                + ",Good row,Fine,5.00,1,Tests,\n"
                + ",X,Too short name,-1,abc,Tests,ftp://bad\n"
                + "999999,Ghost,Missing product,5.00,1,Tests,\n"
                + existing.getId() + ",Dup one,Text,5.00,1,Tests,\n"
                + existing.getId() + ",Dup two,Text,5.00,1,Tests,\n";

        ResponseEntity<ImportReportDto> response = upload(csv, false, ImportReportDto.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isApplied()).isFalse();
        assertThat(response.getBody().getErrors()).extracting(ImportRowErrorDto::getRow, ImportRowErrorDto::getField)
                .contains(
                        org.assertj.core.groups.Tuple.tuple(3, "name"),
                        org.assertj.core.groups.Tuple.tuple(3, "price"),
                        org.assertj.core.groups.Tuple.tuple(3, "stockQuantity"),
                        org.assertj.core.groups.Tuple.tuple(3, "imageUrl"),
                        org.assertj.core.groups.Tuple.tuple(4, "id"),
                        org.assertj.core.groups.Tuple.tuple(6, "id"));
        assertThat(productRepository.findAll()).extracting(ProductEntity::getName).doesNotContain("Good row");
    }

    @Test
    void dryRunValidatesWithoutPersistingAndHeaderProblemsAreReported() {
        ResponseEntity<ImportReportDto> dryRun = upload(
                "name,description,price,stockQuantity,category\nDry lamp,Text,5.00,1,Tests\n", true, ImportReportDto.class);
        ResponseEntity<ImportReportDto> badHeader = upload(
                "name,colour,price\nLamp,red,5.00\n", false, ImportReportDto.class);

        assertThat(dryRun.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dryRun.getBody().isDryRun()).isTrue();
        assertThat(dryRun.getBody().getCreated()).isEqualTo(1);
        assertThat(productRepository.findAll()).extracting(ProductEntity::getName).doesNotContain("Dry lamp");
        assertThat(badHeader.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(badHeader.getBody().getErrors()).extracting(ImportRowErrorDto::getMessage)
                .contains("Unknown column: colour", "Missing required column: category",
                        "Missing required column: description", "Missing required column: stockQuantity");
    }

    @Test
    void malformedCsvAndNonAdminsAreRejected() {
        ResponseEntity<String> unterminated = upload("name,description\n\"open,Text\n", false, String.class);
        HttpHeaders clientHeaders = multipartHeaders(clientToken);
        ResponseEntity<String> client = executePost(IMPORT, body("name\nx\n"), clientHeaders, String.class);

        assertThat(unterminated.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unterminated.getBody()).contains("Malformed CSV");
        assertThat(client.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private <T> ResponseEntity<T> upload(String csv, boolean dryRun, Class<T> type) {
        return executePost(IMPORT + "?dryRun=" + dryRun, body(csv), multipartHeaders(adminToken), type);
    }

    private HttpHeaders multipartHeaders(String token) {
        HttpHeaders headers = getHeadersWith(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return headers;
    }

    private MultiValueMap<String, Object> body(String csv) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "products.csv";
            }
        });
        return body;
    }
}
