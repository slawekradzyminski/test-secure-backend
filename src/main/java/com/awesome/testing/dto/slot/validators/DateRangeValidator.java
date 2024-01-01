package com.awesome.testing.dto.slot.validators;

import com.awesome.testing.dto.slot.CreateSlotRangeDto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class DateRangeValidator implements ConstraintValidator<ValidDateRange, CreateSlotRangeDto> {

    @Override
    public boolean isValid(CreateSlotRangeDto value, ConstraintValidatorContext context) {
        if (value.getStartAvailability() == null || value.getEndAvailability() == null) {
            return true;
        }
        return value.getEndAvailability().isAfter(value.getStartAvailability());
    }
}
