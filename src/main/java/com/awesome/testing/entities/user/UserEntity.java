package com.awesome.testing.entities.user;

import com.awesome.testing.dto.users.Role;

import java.util.List;

public interface UserEntity {

    int getId();
    String getUsername();
    String getEmail();
    List<Role> getRoles();
    String getFirstName();
    String getLastName();
    String getPassword();

    void setId(int id);
    void setUsername(String username);
    void setEmail(String email);
    void setRoles(List<Role> roles);
    void setFirstName(String firstName);
    void setLastName(String lastName);
    void setPassword(String password);

}
