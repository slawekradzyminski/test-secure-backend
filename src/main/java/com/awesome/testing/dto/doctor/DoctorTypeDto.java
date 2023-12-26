package com.awesome.testing.dto.doctor;

import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import lombok.*;

import java.util.List;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DoctorTypeDto {

    int id;
    String doctorType;

    public static DoctorTypeDto from(DoctorTypeEntity doctorTypeEntity) {
        return DoctorTypeDto.builder()
                .id(doctorTypeEntity.getId())
                .doctorType(doctorTypeEntity.getDoctorType())
                .build();
    }

    public static List<DoctorTypeDto> from(List<DoctorTypeEntity> doctorTypeEntities) {
        return doctorTypeEntities.stream()
                .map(DoctorTypeDto::from)
                .toList();
    }
}
