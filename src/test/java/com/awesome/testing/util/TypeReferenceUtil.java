package com.awesome.testing.util;

import com.awesome.testing.dto.UserResponseDTO;
import org.springframework.core.ParameterizedTypeReference;

import java.util.List;
import java.util.Map;

public class TypeReferenceUtil {

    public static ParameterizedTypeReference<Map<String, String>> mapTypeReference() {
        return new ParameterizedTypeReference<>() {
        };
    }

    public static ParameterizedTypeReference<List<UserResponseDTO>> userListTypeReference() {
        return new ParameterizedTypeReference<>() {
        };
    }
}
