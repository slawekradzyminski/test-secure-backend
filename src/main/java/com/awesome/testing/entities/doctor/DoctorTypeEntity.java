package com.awesome.testing.entities.doctor;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

import com.awesome.testing.entities.user.UserEntity;

@Entity
@Setter
@Getter
@Table(name = "doctor_types")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, nullable = false)
    private DoctorType doctorType;

    @ManyToMany(mappedBy = "doctorTypes")
    private List<UserEntity> doctors;
}