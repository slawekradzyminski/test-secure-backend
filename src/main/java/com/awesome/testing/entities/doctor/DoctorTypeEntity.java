package com.awesome.testing.entities.doctor;

import jakarta.persistence.*;
import lombok.*;

import java.util.List;

import com.awesome.testing.entities.user.UserEntity;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

@Entity
@Setter
@Getter
@Table(name = "doctor_types")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
public class DoctorTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(unique = true, nullable = false)
    private String doctorType;

    @JsonBackReference
    @ManyToMany(mappedBy = "doctorTypes")
    private List<UserEntity> doctors;
}