package com.awesome.testing.dto.slot;

import com.awesome.testing.entities.doctor.DoctorTypeEntity;
import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.slot.SlotStatus;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.stream.Collectors;

import com.awesome.testing.entities.user.UserEntity;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotDto {

    private Integer id;
    private String doctorUsername;
    private String clientUsername;
    private String startTime;
    private String endTime;
    private Set<String> doctorSpecialties;
    private SlotStatus status;

    public static SlotDto from(SlotEntity slotEntity, UserEntity doctor) {
        return SlotDto.builder()
                .id(slotEntity.getId())
                .doctorUsername(doctor.getUsername())
                .clientUsername(slotEntity.getClient() != null ? slotEntity.getClient().getUsername() : null)
                .startTime(slotEntity.getStartTime().format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(slotEntity.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME))
                .doctorSpecialties(doctor.getDoctorTypes().stream()
                        .map(DoctorTypeEntity::getDoctorType)
                        .collect(Collectors.toSet()))
                .status(slotEntity.getStatus())
                .build();
    }
}
