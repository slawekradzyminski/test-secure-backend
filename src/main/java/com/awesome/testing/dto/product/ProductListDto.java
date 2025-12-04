package com.awesome.testing.dto.product;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ProductListDto {
    List<ProductDto> products;
    long total;
    int page;
    int size;
}
