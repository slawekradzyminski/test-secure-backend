package com.awesome.testing.dto.users;

import org.springframework.security.core.GrantedAuthority;

public enum Role implements GrantedAuthority {
    ROLE_ADMIN, ROLE_CLIENT, ROLE_DOCTOR;

    public String getAuthority() {
        return name();
    }

}
