package com.awesome.testing.service.tax;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CountryResolverTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "PL|PL", "pl|PL", "POL|PL", "Poland|PL", "poland|PL", "POLAND|PL", "Polska|PL", "  Poland  |PL",
            "UK|GB", "uk|GB", "GB|GB", "GBR|GB", "United Kingdom|GB", "united   kingdom|GB", "Great Britain|GB",
            "U.K.|GB", "England|GB",
            "USA|US", "U.S.A.|US", "US|US", "United States|US", "United States of America|US", "united-states|US",
            "Deutschland|DE", "Germany|DE", "DEU|DE", "de|DE",
            "Hungary|HU", "HUN|HU", "Magyarország|HU",
            "Czech Republic|CZ", "Czechia|CZ", "Česko|CZ",
            "Österreich|AT", "ÖSTERREICH|AT", "Austria|AT",
            "The Netherlands|NL", "Holland|NL", "NLD|NL",
            "Switzerland|CH", "CHE|CH", "Norway|NO", "NOR|NO", "Canada|CA", "CAN|CA",
            "Finland|FI", "Spain|ES", "España|ES", "Greece|GR", "Ireland|IE", "Romania|RO", "Sweden|SE",
            "France|FR", "Italy|IT", "Portugal|PT", "Slovakia|SK", "Slovenia|SI", "Croatia|HR", "Cyprus|CY",
            "Malta|MT", "Luxembourg|LU", "Latvia|LV", "Lithuania|LT", "Estonia|EE", "Denmark|DK",
            "Bulgaria|BG", "Belgium|BE"})
    void resolvesCodesNamesAndVariants(String input, String expected) {
        assertThat(CountryResolver.toIsoCode(input)).contains(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"JP", "jp", " xx "})
    void treatsOtherTwoLetterInputAsAnAlpha2Code(String input) {
        assertThat(CountryResolver.toIsoCode(input)).contains(input.trim().toUpperCase());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "Atlantis", "JPN", "P", "P L L", "Pol and", "12", "Polandia"})
    void returnsEmptyForUnknownInput(String input) {
        assertThat(CountryResolver.toIsoCode(input)).isEmpty();
    }

    @Test
    void normalisesCaseDotsAndSeparators() {
        assertThat(CountryResolver.normalise("  U.S._of--A \t")).isEqualTo("us of a");
    }
}
