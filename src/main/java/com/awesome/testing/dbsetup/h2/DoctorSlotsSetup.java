package com.awesome.testing.dbsetup.h2;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import com.awesome.testing.dto.users.Role;
import com.awesome.testing.dto.users.UserResponseDto;
import com.awesome.testing.service.SlotService;
import com.awesome.testing.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.stream.Stream;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class DoctorSlotsSetup {

    private final SlotService slotService;
    private final UserService userService;

    public void setupSlotsForDoctors() {
        userService.getAll().stream().filter(it -> it.getRoles().contains(Role.ROLE_DOCTOR))
                .forEach(this::setupSlotsForTwoMonthsAhead);
    }

    private void setupSlotsForTwoMonthsAhead(UserResponseDto doctor) {
        LocalDate today = LocalDate.now();
        LocalDate fourMonthsAhead = today.plusMonths(2);

        Stream.iterate(today.plusDays(1), date -> !date.isAfter(fourMonthsAhead), date -> date.plusDays(1))
                .filter(DoctorSlotsSetup::isDayOfWeek)
                .forEach(date -> setupSlots(date, doctor.getUsername()));
    }

    private static boolean isDayOfWeek(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    private void setupSlots(LocalDate date, String username) {
        LocalDateTime morningSlot = LocalDateTime.of(date, LocalTime.of(8, 0));
        LocalDateTime afternoonSlot = LocalDateTime.of(date, LocalTime.of(15, 0));
        slotService.createSlots(CreateSlotRangeDto.builder()
                .username(username)
                .slotDuration(Duration.ofMinutes(60))
                .startAvailability(morningSlot)
                .endAvailability(afternoonSlot)
                .build());
    }
}
