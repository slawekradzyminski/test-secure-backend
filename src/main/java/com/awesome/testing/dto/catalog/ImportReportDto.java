package com.awesome.testing.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of a product CSV import. Nothing is persisted when errors is non-empty or dryRun is true.")
public class ImportReportDto {
    @Schema(description = "Whether this was a dry run", example = "false")
    private boolean dryRun;
    @Schema(description = "Whether changes were persisted", example = "true")
    private boolean applied;
    @Schema(description = "Number of non-empty data rows", example = "3")
    private int totalRows;
    @Schema(description = "Rows that create a product", example = "1")
    private int created;
    @Schema(description = "Rows that change an existing product", example = "1")
    private int updated;
    @Schema(description = "Rows identical to the existing product", example = "1")
    private int unchanged;
    @Builder.Default
    @Schema(description = "Validation errors; empty when the file is valid")
    private List<ImportRowErrorDto> errors = new ArrayList<>();
}
