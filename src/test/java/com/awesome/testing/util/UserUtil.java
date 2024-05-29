package com.awesome.testing.util;

import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.dto.users.Role;
import net.bytebuddy.utility.RandomString;

import java.text.MessageFormat;
import java.util.List;

public class UserUtil {

    public static UserRegisterDTO getRandomUser() {
        return UserRegisterDTO.builder()
                .username(RandomString.make(10))
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

    public static UserRegisterDTO getRandomUserWithUsername(String username) {
        return UserRegisterDTO.builder()
                .username(username)
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

    public static UserRegisterDTO getRandomUserWithRoles(List<Role> roles) {
        return UserRegisterDTO.builder()
                .username(RandomString.make(10))
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(roles)
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

    public static String getRandomEmail() {
        return MessageFormat.format("{0}@slawek.com", RandomString.make(10));
    }

}
