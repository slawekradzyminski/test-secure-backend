package com.awesome.testing.dto.slot;

import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.slot.SlotStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotDto {

    private Integer id;
    private String doctorUsername;
    private String clientUsername;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private SlotStatus status;

    public static SlotDto from(SlotEntity slotEntity) {
        return SlotDto.builder()
                .id(slotEntity.getId())
                .doctorUsername(slotEntity.getDoctor().getUsername())
                .clientUsername(slotEntity.getClient() != null ? slotEntity.getClient().getUsername() : null)
                .startTime(slotEntity.getStartTime())
                .endTime(slotEntity.getEndTime())
                .status(slotEntity.getStatus())
                .build();
    }
}
