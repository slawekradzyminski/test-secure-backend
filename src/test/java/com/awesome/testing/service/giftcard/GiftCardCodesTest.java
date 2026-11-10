package com.awesome.testing.service.giftcard;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;

class GiftCardCodesTest {

    @RepeatedTest(20)
    void generatedCodesUseThreeGroupsOfUnambiguousCharacters() {
        String code = GiftCardCodes.generate(new Random());

        assertThat(code).matches("^GC-[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}$").doesNotContain("O", "I", "L", "0", "1");
        assertThat(GiftCardCodes.isWellFormed(code)).isTrue();
    }

    @Test
    void generationCoversTheWholeAlphabet() {
        RandomGenerator first = new FixedRandom(0);
        RandomGenerator last = new FixedRandom(GiftCardCodes.ALPHABET.length() - 1);

        assertThat(GiftCardCodes.generate(first)).isEqualTo("GC-AAAA-AAAA-AAAA");
        assertThat(GiftCardCodes.generate(last)).isEqualTo("GC-9999-9999-9999");
    }

    @Test
    void normalizesCustomerInput() {
        assertThat(GiftCardCodes.normalize(" gc-abcd-efgh-jkmn ")).isEqualTo("GC-ABCD-EFGH-JKMN");
        assertThat(GiftCardCodes.normalize(null)).isEmpty();
    }

    @Test
    void rejectsCodesOutsideTheFormat() {
        assertThat(GiftCardCodes.isWellFormed("GC-ABCD-EFGH-JKMN")).isTrue();
        assertThat(GiftCardCodes.isWellFormed("GC-ABCD-EFGH-JKM0")).isFalse();
        assertThat(GiftCardCodes.isWellFormed("GC-ABCD-EFGH-JKMNP")).isFalse();
        assertThat(GiftCardCodes.isWellFormed("XGC-ABCD-EFGH-JKMN")).isFalse();
        assertThat(GiftCardCodes.isWellFormed("gc-abcd-efgh-jkmn")).isFalse();
        assertThat(GiftCardCodes.isWellFormed("")).isFalse();
    }

    private record FixedRandom(int value) implements RandomGenerator {
        @Override
        public long nextLong() {
            return value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
