package com.awesome.testing.dto.slot;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSlotRangeDto {

    String username;

    @FutureOrPresent
    LocalDateTime startAvailability;

    @Future
    LocalDateTime endAvailability;

    @MinDuration(value = 30, message = "Slot duration must be at least 30 minutes")
    Duration slotDuration;

}
