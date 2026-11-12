package com.awesome.testing.dto.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class VisibleTextValidator implements ConstraintValidator<VisibleText, CharSequence> {

    /** Letters and symbols that render as blank space (Hangul fillers, braille blank). */
    private static final Set<Integer> BLANK_GLYPHS = Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800);

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || hasVisibleCharacter(value);
    }

    public static boolean hasVisibleCharacter(CharSequence value) {
        return value.codePoints().anyMatch(VisibleTextValidator::isVisible);
    }

    private static boolean isVisible(int codePoint) {
        if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || BLANK_GLYPHS.contains(codePoint)) {
            return false;
        }
        int type = Character.getType(codePoint);
        return type != Character.CONTROL && type != Character.FORMAT && type != Character.UNASSIGNED
                && type != Character.PRIVATE_USE && type != Character.SURROGATE;
    }
}
