package com.awesome.testing.dto.purchasing;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Supplier")
public class SupplierDto {
    @Schema(description = "Supplier ID", example = "1")
    private Long id;
    @Schema(description = "Name", example = "Nordic Electronics")
    private String name;
    @Schema(description = "Purchasing contact", example = "orders@nordic.example")
    private String email;
    @Schema(description = "Lead time in days", example = "7")
    private Integer leadTimeDays;
    @Schema(type = "string", format = "local-date-time", description = "Creation timestamp")
    private LocalDateTime createdAt;
}
