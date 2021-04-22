package com.awesome.testing.util;

import com.awesome.testing.dto.UserDataDTO;
import com.awesome.testing.model.Role;
import net.bytebuddy.utility.RandomString;

import java.text.MessageFormat;
import java.util.List;

public class UserUtil {

    public static UserDataDTO getRandomUser() {
        return UserDataDTO.builder()
                .username(RandomString.make(10))
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(List.of(Role.ROLE_ADMIN))
                .build();
    }

    public static UserDataDTO getRandomUserWithUsername(String username) {
        return UserDataDTO.builder()
                .username(username)
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(List.of(Role.ROLE_ADMIN))
                .build();
    }

    public static UserDataDTO getRandomUserWithRoles(List<Role> roles) {
        return UserDataDTO.builder()
                .username(RandomString.make(10))
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(roles)
                .build();
    }

    private static String getRandomEmail() {
        return MessageFormat.format("{0}@slawek.com", RandomString.make(10));
    }

}
