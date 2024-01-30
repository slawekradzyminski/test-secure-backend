package com.awesome.testing.controller.slot;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;

import com.awesome.testing.entities.slot.SlotStatus;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Validated
@RequiredArgsConstructor
@Data
public class SlotSearchCriteria {

    @Schema(type="string", example = "2024-01-10T00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime startTime;

    @Schema(type="string", example = "2024-01-11T00:00:00")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private final LocalDateTime endTime;

    @Schema(example = "doctor")
    @Size(min = 3, message = "Doctor username must have at least 3 chars")
    private final String doctorUsername;

    @Schema(example = "AVAILABLE")
    private final SlotStatus slotStatus;

    @Schema(example = "1")
    @Positive
    private final Integer specialtyId;

    @Hidden
    @AssertTrue(message = "startTime must be before endTime")
    public boolean isTimeRangeValid() {
        return startTime.isBefore(endTime);
    }

}
