package com.awesome.testing.util;

import com.awesome.testing.dto.UserRegisterDto;
import com.awesome.testing.model.Role;
import lombok.experimental.UtilityClass;
import net.bytebuddy.utility.RandomString;

import java.text.MessageFormat;
import java.util.List;

@UtilityClass
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
                .password(RandomString.make(12))
                .roles(List.of(Role.ROLE_ADMIN))
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

    public static UserRegisterDto getRandomUserWithRoles(List<Role> roles) {
        return UserRegisterDto.builder()
                .username(RandomString.make(10))
                .email(getRandomEmail())
                .password(RandomString.make(12))
                .roles(roles)
                .firstName(RandomString.make(10))
                .lastName(RandomString.make(10))
                .build();
    }

    public static String getRandomEmail() {
        return MessageFormat.format("{0}@slawek.com", RandomString.make(10));
    }

}
