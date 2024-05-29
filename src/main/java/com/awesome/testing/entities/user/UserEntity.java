package com.awesome.testing.entities.user;

import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserRegisterDTO;
import com.awesome.testing.entities.doctor.DoctorTypeEntity;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

import jakarta.validation.constraints.Size;

@Entity
@ToString
@Setter
@Getter
@Table(name = "users")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

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

    @ManyToMany
    @JoinTable(
        name = "user_doctor_type",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "doctor_type_id"))
    private List<DoctorTypeEntity> doctorTypes;

    public static UserEntity from(UserRegisterDTO userRegisterDTO, String encryptedPassword) {
        return UserEntity.builder()
                .username(userRegisterDTO.getUsername())
                .password(encryptedPassword)
                .roles(userRegisterDTO.getRoles())
                .email(userRegisterDTO.getEmail())
                .firstName(userRegisterDTO.getFirstName())
                .lastName(userRegisterDTO.getLastName())
                .build();
    }

    public static UserEntity from(UserRegisterDTO randomUser) {
        return from(randomUser, randomUser.getPassword());
    }
}
