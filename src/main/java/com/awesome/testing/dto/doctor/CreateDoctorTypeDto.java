package com.awesome.testing.dto.doctor;

import com.awesome.testing.entities.doctor.DoctorType;
import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDoctorTypeDto {

    DoctorType doctorType;

}
