package com.awesome.testing.dto.slot;

import com.awesome.testing.dto.slot.validators.ValidDateRange;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

import com.awesome.testing.dto.slot.validators.MinDuration;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange
public class CreateSlotRangeDto {

    String username;

    @FutureOrPresent
    LocalDateTime startAvailability;

    @Future
    LocalDateTime endAvailability;

    @MinDuration(value = 30, message = "Slot duration must be at least 30 minutes")
    Duration slotDuration;

}
