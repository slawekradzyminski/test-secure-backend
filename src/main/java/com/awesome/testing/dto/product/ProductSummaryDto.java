package com.awesome.testing.dto.product;

import com.awesome.testing.entity.ProductEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSummaryDto {

    private Long id;
    private String name;

    public static ProductSummaryDto from(ProductEntity productEntity) {
        return ProductSummaryDto.builder()
                .id(productEntity.getId())
                .name(productEntity.getName())
                .build();
    }
}

