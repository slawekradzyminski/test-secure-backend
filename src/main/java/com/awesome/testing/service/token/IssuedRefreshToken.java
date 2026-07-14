package com.awesome.testing.service.token;

import com.awesome.testing.entity.UserEntity;

public record IssuedRefreshToken(String value, UserEntity user) {
}
