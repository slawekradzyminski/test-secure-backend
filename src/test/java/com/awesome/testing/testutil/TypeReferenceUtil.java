package com.awesome.testing.testutil;

import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;

public class TypeReferenceUtil {

    public static ParameterizedTypeReference<Map<String, String>> mapTypeReference() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
