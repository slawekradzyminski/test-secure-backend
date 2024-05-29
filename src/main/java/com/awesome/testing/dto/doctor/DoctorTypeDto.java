package com.awesome.testing.dto.doctor;

import com.awesome.testing.entities.doctor.DoctorType;
import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorTypeDto {

    int id;
    DoctorType doctorType;

    public static DoctorTypeDto from(DoctorTypeEntity doctorTypeEntity) {
        return DoctorTypeDto.builder()
                .id(doctorTypeEntity.getId())
                .doctorType(doctorTypeEntity.getDoctorType())
                .build();
    }
}
