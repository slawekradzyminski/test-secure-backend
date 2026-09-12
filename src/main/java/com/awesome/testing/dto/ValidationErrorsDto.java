package com.awesome.testing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.HashMap;

/** Field names (or "error" for invalid arguments) mapped to diagnostic messages. */
@Schema(description = "Validation messages keyed by field name; invalid arguments use the error key",
        example = "{\"email\":\"Email should be valid\"}")
public class ValidationErrorsDto extends HashMap<String, String> {
    private static final long serialVersionUID = 1L;
}
