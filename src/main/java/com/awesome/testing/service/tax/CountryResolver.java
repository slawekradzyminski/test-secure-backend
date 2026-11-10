package com.awesome.testing.service.tax;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns the free-text shipping country into an ISO 3166-1 alpha-2 code.
 *
 * <p>Accepts alpha-2 and alpha-3 codes, English names and common variants
 * ("UK", "Great Britain", "USA", "Polska", "Deutschland", ...), ignoring case,
 * dots and repeated whitespace. Any other two-letter input is taken as an
 * alpha-2 code, so a rate an administrator adds for a new country works with
 * its code. The table is static on purpose: locale display names differ
 * between JDKs and are not guaranteed in the native image.
 */
public final class CountryResolver {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s_-]+");
    private static final Pattern ALPHA_2 = Pattern.compile("[a-z]{2}");
    private static final Map<String, String> ALIASES = new HashMap<>();

    static {
        country("AT", "AUT", "Austria", "Österreich", "Osterreich", "Oesterreich");
        country("BE", "BEL", "Belgium", "Belgique", "België", "Belgie", "Belgien");
        country("BG", "BGR", "Bulgaria");
        country("HR", "HRV", "Croatia", "Hrvatska");
        country("CY", "CYP", "Cyprus");
        country("CZ", "CZE", "Czechia", "Czech Republic", "Česko", "Cesko");
        country("DK", "DNK", "Denmark", "Danmark");
        country("EE", "EST", "Estonia", "Eesti");
        country("FI", "FIN", "Finland", "Suomi");
        country("FR", "FRA", "France");
        country("DE", "DEU", "Germany", "Deutschland");
        country("GR", "GRC", "Greece", "Hellas");
        country("HU", "HUN", "Hungary", "Magyarország", "Magyarorszag");
        country("IE", "IRL", "Ireland", "Republic of Ireland", "Éire", "Eire");
        country("IT", "ITA", "Italy", "Italia");
        country("LV", "LVA", "Latvia", "Latvija");
        country("LT", "LTU", "Lithuania", "Lietuva");
        country("LU", "LUX", "Luxembourg");
        country("MT", "MLT", "Malta");
        country("NL", "NLD", "Netherlands", "The Netherlands", "Holland", "Nederland");
        country("PL", "POL", "Poland", "Polska");
        country("PT", "PRT", "Portugal");
        country("RO", "ROU", "Romania", "România");
        country("SK", "SVK", "Slovakia", "Slovak Republic", "Slovensko");
        country("SI", "SVN", "Slovenia", "Slovenija");
        country("ES", "ESP", "Spain", "España", "Espana");
        country("SE", "SWE", "Sweden", "Sverige");
        country("GB", "GBR", "United Kingdom", "UK", "Great Britain", "Britain", "England", "Scotland", "Wales",
                "Northern Ireland", "United Kingdom of Great Britain and Northern Ireland");
        country("CH", "CHE", "Switzerland", "Schweiz", "Suisse", "Svizzera");
        country("NO", "NOR", "Norway", "Norge");
        country("US", "USA", "United States", "United States of America", "America", "US of A");
        country("CA", "CAN", "Canada");
    }

    private CountryResolver() {
    }

    /** The upper-case alpha-2 code for {@code country}, or empty when it is blank or not recognised. */
    public static Optional<String> toIsoCode(String country) {
        if (country == null) {
            return Optional.empty();
        }
        String key = normalise(country);
        String code = ALIASES.get(key);
        if (code != null) {
            return Optional.of(code);
        }
        return ALPHA_2.matcher(key).matches() ? Optional.of(key.toUpperCase(Locale.ROOT)) : Optional.empty();
    }

    static String normalise(String value) {
        return SEPARATORS.matcher(value.replace(".", "").toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private static void country(String alpha2, String alpha3, String... names) {
        ALIASES.put(normalise(alpha2), alpha2);
        ALIASES.put(normalise(alpha3), alpha2);
        for (String name : names) {
            ALIASES.put(normalise(name), alpha2);
        }
    }
}
