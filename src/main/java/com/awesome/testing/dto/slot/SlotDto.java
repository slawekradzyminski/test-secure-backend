package com.awesome.testing.dto.slot;

import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.slot.SlotStatus;
import java.time.format.DateTimeFormatter;
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
    private SlotStatus status;

    public static SlotDto from(SlotEntity slotEntity) {
        return SlotDto.builder()
                .id(slotEntity.getId())
                .doctorUsername(slotEntity.getDoctor().getUsername())
                .clientUsername(slotEntity.getClient() != null ? slotEntity.getClient().getUsername() : null)
                .startTime(slotEntity.getStartTime().format(DateTimeFormatter.ISO_DATE_TIME))
                .endTime(slotEntity.getEndTime().format(DateTimeFormatter.ISO_DATE_TIME))
                .status(slotEntity.getStatus())
                .build();
    }
}
