package com.awesome.testing.security;

import com.awesome.testing.service.identity.UserIdentity;
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

    private final UserIdentity userIdentity;

    /** Exact username first, else the only case-insensitive match; the details carry the stored username. */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userIdentity.findBySignInName(username)
                .map(this::toUserDetails)
                .orElseThrow(() -> notFound(username));
    }

    private UserDetails toUserDetails(UserEntity user) {
        return withUsername(user.getUsername())
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
