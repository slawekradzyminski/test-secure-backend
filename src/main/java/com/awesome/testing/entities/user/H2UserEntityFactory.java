package com.awesome.testing.entities.user;

import com.awesome.testing.dto.users.UserRegisterDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class H2UserEntityFactory implements UserEntityFactory<H2UserEntity> {

    @Override
    public H2UserEntity createUser(UserRegisterDto dto, String encryptedPassword) {
        return H2UserEntity.builder()
                .username(dto.getUsername())
                .password(encryptedPassword)
                .email(dto.getEmail())
                .roles(dto.getRoles())
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .build();
    }
}
