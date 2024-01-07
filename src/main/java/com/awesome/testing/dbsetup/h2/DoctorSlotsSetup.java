package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static com.awesome.testing.dbsetup.h2.InitialUsers.getDoctor;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DoctorSlotsSetup {

    static final LocalDateTime TOMORROW_MORNING = LocalDateTime.of(
            LocalDate.now().plusDays(1),
            LocalTime.of(7, 0)
    );

    static final LocalDateTime TOMORROW_AFTERNOON = LocalDateTime.of(
            LocalDate.now().plusDays(1),
            LocalTime.of(15, 0)
    );

    private final SlotService slotService;

    public void setupSlotsForTomorrow() {
        slotService.createSlots(CreateSlotRangeDto.builder()
                .username(getDoctor().getUsername())
                .slotDuration(Duration.ofMinutes(60))
                .startAvailability(TOMORROW_MORNING)
                .endAvailability(TOMORROW_AFTERNOON)
                .build());
    }
}
