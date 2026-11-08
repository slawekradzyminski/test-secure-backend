package com.awesome.testing.dto.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Validation problem in one CSV row")
public class ImportRowErrorDto {
    @Schema(description = "1-based line number in the file; the header is line 1", example = "4")
    private int row;
    @Schema(description = "Column name, or 'file' for structural problems", example = "price")
    private String field;
    @Schema(description = "Problem description", example = "Price must be greater than 0")
    private String message;
}
