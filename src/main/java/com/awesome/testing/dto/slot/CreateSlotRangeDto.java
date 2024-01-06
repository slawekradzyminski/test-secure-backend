package com.awesome.testing.dto.slot;

import com.awesome.testing.dto.slot.validators.ValidDateRange;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.Duration;
import java.time.LocalDateTime;

import com.awesome.testing.dto.slot.validators.MaxAvailability;
import com.awesome.testing.dto.slot.validators.MinDuration;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidDateRange
@MaxAvailability
public class CreateSlotRangeDto {

    @NotNull
    String username;

    @NotNull
    @FutureOrPresent
    LocalDateTime startAvailability;

    @NotNull
    @Future
    LocalDateTime endAvailability;

    @MinDuration(value = 30, message = "Slot duration must be at least 30 minutes")
    Duration slotDuration;

}
