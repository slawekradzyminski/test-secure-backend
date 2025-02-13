package com.awesome.testing.entity;

import com.awesome.testing.dto.order.AddressDto;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressEntity {
    private String street;
    private String city;
    private String state;
    private String zipCode;
    private String country;

    public static AddressEntity from(AddressDto dto) {
        return AddressEntity.builder()
                .street(dto.getStreet())
                .city(dto.getCity())
                .state(dto.getState())
                .zipCode(dto.getZipCode())
                .country(dto.getCountry())
                .build();
    }
} 