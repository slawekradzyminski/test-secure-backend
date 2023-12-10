package com.awesome.testing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

import jakarta.validation.constraints.Size;

@Entity
@ToString
@Setter
@Getter
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Size(min = 4, message = "Minimum password length: 4 characters")
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<Role> roles;

    @Size(min = 4, message = "Minimum firstName length: 4 characters")
    @Column(nullable = false)
    private String firstName;

    @Size(min = 4, message = "Minimum lastName length: 4 characters")
    @Column(nullable = false)
    private String lastName;

}
