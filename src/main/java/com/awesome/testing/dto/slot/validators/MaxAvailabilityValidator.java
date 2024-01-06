package com.awesome.testing.dto.slot.validators;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Duration;

public class MaxAvailabilityValidator implements ConstraintValidator<MaxAvailability, CreateSlotRangeDto> {

    @Override
    public boolean isValid(CreateSlotRangeDto value, ConstraintValidatorContext context) {
        return Duration.between(value.getStartAvailability(), value.getEndAvailability()).toHours() <= 8;
    }
}
