package com.awesome.testing.util;

import com.awesome.testing.dto.product.ProductDto;
import com.awesome.testing.dto.user.UserResponseDto;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;

public class TypeReferenceUtil {

    public static ParameterizedTypeReference<Map<String, String>> mapTypeReference() {
        return new ParameterizedTypeReference<>() {
        };
    }

    public static ParameterizedTypeReference<List<UserResponseDto>> userListTypeReference() {
        return new ParameterizedTypeReference<>() {
        };
    }

    public static ParameterizedTypeReference<List<ProductDto>> productListTypeReference() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
