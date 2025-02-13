package com.awesome.testing.security;

import com.awesome.testing.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.awesome.testing.entity.UserEntity;

import java.text.MessageFormat;

import static org.springframework.security.core.userdetails.User.*;

@Service
@RequiredArgsConstructor
public class MyUserDetails implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .map(user -> toUserDetails(username, user))
                .orElseThrow(() -> notFound(username));
    }

    private UserDetails toUserDetails(String username, UserEntity user) {
        return withUsername(username)
                .password(user.getPassword())
                .authorities(user.getRoles())
                .accountExpired(false)
                .accountLocked(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    private UsernameNotFoundException notFound(String username) {
        return new UsernameNotFoundException(
                MessageFormat.format("User ''{0}'' not found", username));
    }

}
