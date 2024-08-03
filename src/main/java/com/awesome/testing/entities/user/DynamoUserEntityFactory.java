package com.awesome.testing.entities.user;

import com.awesome.testing.dto.users.UserRegisterDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DynamoUserEntityFactory implements UserEntityFactory<DynamoUserEntity> {

    @Override
    public DynamoUserEntity createUser(UserRegisterDto dto, String encryptedPassword) {
        DynamoUserEntity user = new DynamoUserEntity();
        user.setUsername(dto.getUsername());
        user.setPassword(encryptedPassword);
        user.setEmail(dto.getEmail());
        user.setRoles(dto.getRoles());
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        return user;
    }
}
