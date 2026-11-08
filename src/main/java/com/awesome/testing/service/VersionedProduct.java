package com.awesome.testing.service;

import com.awesome.testing.dto.product.ProductDto;

/** A product together with its optimistic-locking version, exposed as an HTTP entity tag. */
public record VersionedProduct(ProductDto product, Long version) {

    public String etag() {
        return "\"" + version + "\"";
    }
}
