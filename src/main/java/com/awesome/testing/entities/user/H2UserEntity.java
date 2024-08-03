package com.awesome.testing.entities.user;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDto;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

import jakarta.validation.constraints.Size;
import org.springframework.context.annotation.Profile;

@Profile("dev")
@Entity
@ToString
@Setter
@Getter
@Table(name = "users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
public class H2UserEntity implements UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Size(min = 3, max = 255, message = "Minimum username length: 3 characters")
    @Column(unique = true, nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Size(min = 3, message = "Minimum password length: 3 characters")
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    private List<Role> roles;

    @Size(min = 3, message = "Minimum firstName length: 3 characters")
    @Column(nullable = false)
    private String firstName;

    @Size(min = 3, message = "Minimum lastName length: 3 characters")
    @Column(nullable = false)
    private String lastName;

    public static H2UserEntity from(UserRegisterDto userRegisterDto, String encryptedPassword) {
        return H2UserEntity.builder()
                .username(userRegisterDto.getUsername())
                .password(encryptedPassword)
                .roles(userRegisterDto.getRoles())
                .email(userRegisterDto.getEmail())
                .firstName(userRegisterDto.getFirstName())
                .lastName(userRegisterDto.getLastName())
                .build();
    }

    public static H2UserEntity from(UserRegisterDto userRegisterDto) {
        return from(userRegisterDto, userRegisterDto.getPassword());
    }
}
