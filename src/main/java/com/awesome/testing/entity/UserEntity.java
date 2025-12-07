package com.awesome.testing.entity;

import com.awesome.testing.dto.user.Role;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@RequiredArgsConstructor
@Table(name = "app_user")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Size(min = 4, max = 255, message = "Minimum username length: 4 characters")
    @Column(unique = true, nullable = false)
    @NonNull
    private String username;

    @Column(unique = true, nullable = false)
    @NonNull
    private String email;

    @Size(min = 8, message = "Minimum password length: 8 characters")
    @NonNull
    private String password;

    @ElementCollection(fetch = FetchType.EAGER)
    List<Role> roles;

    @Size(max = 255)
    @Column(name = "first_name")
    private String firstName;

    @Size(max = 255)
    @Column(name = "last_name")
    private String lastName;

    @Size(max = 5000, message = "Chat system prompt must be at most 5000 characters")
    @Column(name = "chat_system_prompt")
    private String chatSystemPrompt;

    @Size(max = 5000, message = "Tool system prompt must be at most 5000 characters")
    @Column(name = "tool_system_prompt")
    private String toolSystemPrompt;

    @JsonIgnore
    public String getPassword() {
        return password;
    }

}
