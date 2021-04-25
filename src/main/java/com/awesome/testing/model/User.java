package com.awesome.testing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.validation.constraints.Size;

@Entity
@ToString
@Setter
@Getter
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
