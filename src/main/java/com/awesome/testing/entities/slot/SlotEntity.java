package com.awesome.testing.entities.slot;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import com.awesome.testing.entities.user.UserEntity;

@Entity
@Setter
@Getter
@Table(name = "slots", uniqueConstraints = @UniqueConstraint(columnNames = {"doctor_id", "start_time"}))
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "doctor_id", nullable = false)
    private UserEntity doctor;

    @ManyToOne
    @JoinColumn(name = "client_id")
    private UserEntity client;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

}