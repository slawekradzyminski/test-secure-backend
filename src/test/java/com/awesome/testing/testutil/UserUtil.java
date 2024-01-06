package com.awesome.testing.testutil;

import com.awesome.testing.dto.users.UserRegisterDto;
import com.awesome.testing.dto.users.Role;
import net.bytebuddy.utility.RandomString;

import java.text.MessageFormat;
import java.util.List;

public class UserUtil {

    public static UserRegisterDto getRandomUser() {
        return UserRegisterDto.builder()
                .username(RandomString.make(10))
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

    public static UserRegisterDto getRandomUserWithUsername(String username) {
        return UserRegisterDto.builder()
                .username(username)
                .email(getRandomEmail())
                .password(RandomString.make(10))
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

    public static UserRegisterDto getRandomUserWithRoles(List<Role> roles) {
        return UserRegisterDto.builder()
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
