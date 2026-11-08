package com.awesome.testing.service.catalog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvCodecTest {

    @Test
    void textCellsThatLookLikeFormulasArePrefixedWithAQuote() {
        assertThat(CsvCodec.textCell("=SUM(A1:A2)")).isEqualTo("'=SUM(A1:A2)");
        assertThat(CsvCodec.textCell("+48 123")).isEqualTo("'+48 123");
        assertThat(CsvCodec.textCell("-x")).isEqualTo("'-x");
        assertThat(CsvCodec.textCell("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvCodec.textCell("\tTab")).isEqualTo("'\tTab");
        assertThat(CsvCodec.textCell("Plain")).isEqualTo("Plain");
        assertThat(CsvCodec.textCell(null)).isEmpty();
    }

    @Test
    void numericCellsAreNeverPrefixed() {
        assertThat(CsvCodec.cell("-5")).isEqualTo("-5");
    }

    @Test
    void cellsWithSeparatorsQuotesOrLineBreaksAreQuoted() {
        assertThat(CsvCodec.cell("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvCodec.cell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvCodec.cell("two\nlines")).isEqualTo("\"two\nlines\"");
    }

    @Test
    void parserHandlesQuotesEmbeddedLineBreaksCrlfAndBom() {
        List<List<String>> records = CsvCodec.parse("﻿a,b\r\n\"x,1\",\"he said \"\"no\"\"\"\r\n\"multi\nline\",\n");

        assertThat(records).containsExactly(
                List.of("a", "b"),
                List.of("x,1", "he said \"no\""),
                List.of("multi\nline", ""));
    }

    @Test
    void parserKeepsTheLastRecordWithoutATrailingNewline() {
        assertThat(CsvCodec.parse("a,b\n1,2")).containsExactly(List.of("a", "b"), List.of("1", "2"));
    }

    @Test
    void malformedQuotingIsRejected() {
        assertThatThrownBy(() -> CsvCodec.parse("a,\"open\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CsvCodec.parse("a\"b\n")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CsvCodec.parse("\"a\"b\n")).isInstanceOf(IllegalArgumentException.class);
    }
}
