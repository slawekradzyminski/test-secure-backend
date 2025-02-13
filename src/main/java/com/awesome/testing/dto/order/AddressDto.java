package com.awesome.testing.dto.order;

import com.awesome.testing.entity.AddressEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Address data transfer object")
public class AddressDto {
    @NotBlank(message = "Street is required")
    @Schema(description = "Street address", example = "123 Main St")
    private String street;

    @NotBlank(message = "City is required")
    @Schema(description = "City", example = "New York")
    private String city;

    @NotBlank(message = "State is required")
    @Schema(description = "State", example = "NY")
    private String state;

    @NotBlank(message = "Zip code is required")
    @Pattern(regexp = "^[0-9]{2}(-[0-9]{3})?|[0-9]{5}(-[0-9]{4})?$", message = "Invalid postal/zip code format")
    @Schema(description = "Postal/ZIP code", example = "35-119")
    private String zipCode;

    @NotBlank(message = "Country is required")
    @Schema(description = "Country", example = "Poland")
    private String country;

    public static AddressDto from(AddressEntity address) {
        return AddressDto.builder()
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zipCode(address.getZipCode())
                .country(address.getCountry())
                .build();
    }
} 