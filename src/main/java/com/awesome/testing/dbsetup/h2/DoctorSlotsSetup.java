package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.service.SlotService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Stream;

import static com.awesome.testing.dbsetup.h2.InitialUsers.getDoctor;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DoctorSlotsSetup {

    private final SlotService slotService;

    public void setupSlotsForMonthsAhead() {
        LocalDate today = LocalDate.now();
        LocalDate fourMonthsAhead = today.plusMonths(4);

        Stream.iterate(today.plusDays(1), date -> !date.isAfter(fourMonthsAhead), date -> date.plusDays(1))
                .filter(DoctorSlotsSetup::isDayOfWeek)
                .forEach(this::setupSlots);
    }

    private static boolean isDayOfWeek(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private void setupSlots(LocalDate date) {
        LocalDateTime morningSlot = LocalDateTime.of(date, LocalTime.of(7, 0));
        LocalDateTime afternoonSlot = LocalDateTime.of(date, LocalTime.of(15, 0));
        slotService.createSlots(CreateSlotRangeDto.builder()
                .username(getDoctor().getUsername())
                .slotDuration(Duration.ofMinutes(60))
                .startAvailability(morningSlot)
                .endAvailability(afternoonSlot)
                .build());
    }
}
