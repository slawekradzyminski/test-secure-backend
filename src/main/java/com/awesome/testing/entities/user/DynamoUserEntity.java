package com.awesome.testing.entities.user;

import com.awesome.testing.dto.users.Role;
import lombok.Setter;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.util.List;

@Setter
@DynamoDbBean
@Profile("prod")
public class DynamoUserEntity implements UserEntity {

    private int id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private List<Role> roles;

    @DynamoDbPartitionKey
    public int getId() {
        return id;
    }

    @DynamoDbAttribute("Username")
    public String getUsername() {
        return username;
    }

    @DynamoDbAttribute("Email")
    public String getEmail() {
        return email;
    }

    @DynamoDbAttribute("Roles")
    public List<Role> getRoles() {
        return roles;
    }

    @DynamoDbAttribute("FirstName")
    public String getFirstName() {
        return firstName;
    }

    @DynamoDbAttribute("LastName")
    public String getLastName() {
        return lastName;
    }

    @DynamoDbAttribute("Password")
    public String getPassword() {
        return password;
    }
}
