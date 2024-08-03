package com.awesome.testing.security;

import com.awesome.testing.entities.user.UserEntity;
import com.awesome.testing.repository.IUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;

import static org.springframework.security.core.userdetails.User.*;

@Service
@RequiredArgsConstructor
public class MyUserDetails<T extends UserEntity> implements UserDetailsService {

    private final IUserRepository<T> userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserEntity userEntity = userRepository.findByUsername(username);

        if (userEntity == null) {
            throw new UsernameNotFoundException(
                    MessageFormat.format("User ''{0}'' not found", username));
        }

        return withUsername(username)
                .password(userEntity.getPassword())
                .authorities(userEntity.getRoles())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

}
