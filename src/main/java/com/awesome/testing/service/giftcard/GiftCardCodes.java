package com.awesome.testing.service.giftcard;

import lombok.experimental.UtilityClass;

import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Gift card codes look like {@code GC-7KQ2-M9XD-4HPA}: three groups of four
 * characters from an alphabet without look-alikes (no 0/O, 1/I/L). Input is
 * trimmed and upper-cased before it is checked.
 */
@UtilityClass
public class GiftCardCodes {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final Pattern FORMAT = Pattern.compile("^GC-[" + ALPHABET + "]{4}-[" + ALPHABET + "]{4}-[" + ALPHABET + "]{4}$");
    /** A new random code; pass a {@link java.security.SecureRandom} in production. */
    public static String generate(RandomGenerator random) {
        StringBuilder code = new StringBuilder("GC");
        for (int group = 0; group < 3; group++) {
            code.append('-');
            for (int i = 0; i < 4; i++) {
                code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            }
        }
        return code.toString();
    }

    public static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    /** True when the normalized code has the generated format. */
    public static boolean isWellFormed(String normalizedCode) {
        return FORMAT.matcher(normalizedCode).matches();
    }
}
