package com.awesome.testing.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

/**
 * User details that carry the account's access-token version (catalog X-08). A token issued for an older
 * version was revoked by a logout or password reset.
 */
public class VersionedUserDetails extends User {

    private static final long serialVersionUID = 1L;

    private final int tokenVersion;

    public VersionedUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities,
                                int tokenVersion) {
        super(username, password, true, true, true, true, authorities);
        this.tokenVersion = tokenVersion;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
