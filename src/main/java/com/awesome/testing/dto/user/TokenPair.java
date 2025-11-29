package com.awesome.testing.dto.user;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TokenPair {
    String token;
    String refreshToken;
}
