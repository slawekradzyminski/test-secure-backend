package com.awesome.testing.repository.search;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchFoldingTest {

    @Test
    void everyAccentedLetterHasOnePlainLetter() {
        assertThat(SearchFolding.ACCENTED).hasSameSizeAs(SearchFolding.PLAIN);
        assertThat(SearchFolding.PLAIN).matches("[a-z]+");
    }

    @Test
    void foldsPolishAndOtherLatinLettersAndKeepsTheRest() {
        assertThat(SearchFolding.fold("zażółć gęślą jaźń ŁÓŹ")).isEqualTo("zazolc gesla jazn loz");
        assertThat(SearchFolding.fold("crème brûlée, smørrebrød, příliš")).isEqualTo("creme brulee, smorrebrod, prilis");
        assertThat(SearchFolding.fold("100% plain_text\\")).isEqualTo("100% plain_text\\");
    }
}
