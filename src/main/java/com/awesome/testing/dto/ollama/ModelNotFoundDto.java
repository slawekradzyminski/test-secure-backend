package com.awesome.testing.dto.ollama;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelNotFoundDto {

    @Schema(description = "Missing model error. You need to pull it", example = "model 'X' not found")
    private String error;
}