package com.awesome.testing.factory;

import com.awesome.testing.entities.slot.SlotEntity;
import com.awesome.testing.entities.slot.SlotStatus;
import com.awesome.testing.entities.user.UserEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;

import java.time.LocalDateTime;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class SlotEntityFactory {

    public static SlotEntity createSlotEntityWithDoctor(String doctorUsername) {
        return SlotEntity.builder()
            .doctor(UserEntity.builder()
                .username(doctorUsername)
                .build())
            .startTime(LocalDateTime.now())
            .endTime(LocalDateTime.now().plusHours(1))
            .status(SlotStatus.AVAILABLE)
            .build();
    }

    public static SlotEntity createSlotEntityWithClient(UserEntity client) {
        return SlotEntity.builder()
                .doctor(UserEntity.builder()
                        .username(RandomStringUtils.randomAlphanumeric(5))
                        .build())
                .startTime(LocalDateTime.now())
                .client(client)
                .endTime(LocalDateTime.now().plusHours(1))
                .status(SlotStatus.AVAILABLE)
                .build();
    }
}
