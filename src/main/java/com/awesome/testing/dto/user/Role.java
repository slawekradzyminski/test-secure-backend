package com.awesome.testing.dto.user;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    ROLE_ADMIN, ROLE_CLIENT, ROLE_WAREHOUSE;

    @Override
    public String getAuthority() {
        return name();
    }

}
