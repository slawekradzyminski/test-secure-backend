package com.awesome.testing.dto.doctor;

import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorTypeDto {

    int id;
    String doctorType;

    public static DoctorTypeDto from(DoctorTypeEntity doctorTypeEntity) {
        return DoctorTypeDto.builder()
                .id(doctorTypeEntity.getId())
                .doctorType(doctorTypeEntity.getDoctorType())
                .build();
    }
}
