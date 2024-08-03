package com.awesome.testing.entities.user;

import com.awesome.testing.dto.users.UserRegisterDto;

public interface UserEntityFactory<T> {
    T createUser(UserRegisterDto dto, String encryptedPassword);
}
