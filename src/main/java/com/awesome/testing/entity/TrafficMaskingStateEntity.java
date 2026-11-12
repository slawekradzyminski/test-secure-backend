package com.awesome.testing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** The single row recording which masking rules the stored traffic history was masked with. */
@Entity
@Table(name = "traffic_masking_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TrafficMaskingStateEntity {

    public static final short ID = 1;

    @Id
    private Short id;

    @Column(name = "rules_version", nullable = false)
    private int rulesVersion;
}
