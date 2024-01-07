package com.awesome.testing.controller.slot;

import java.time.LocalDateTime;

import org.springframework.validation.annotation.Validated;

import com.awesome.testing.entities.slot.SlotStatus;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Data
public class SlotSearchCriteria {

    private final LocalDateTime startTime;

    private final LocalDateTime endTime;

    @Size(min = 3, max = 50, message = "Doctor username must be between 3 and 50 characters")
    private final String doctorUsername;

    private final SlotStatus slotStatus;

    @Positive
    private final Integer doctorTypeId;

    @AssertTrue(message = "startTime must be before endTime")
    public boolean isTimeRangeValid() {
        return startTime.isBefore(endTime);
    }

}
