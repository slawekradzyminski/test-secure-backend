package com.awesome.testing.repository.search;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;

/**
 * Accent folding for product search: Polish letters and the common Latin
 * accented letters compare as their plain ASCII base letter, so "zazolc"
 * finds "Zażółć". The same table runs in SQL ({@code translate}, available in
 * PostgreSQL and H2, no extension needed) and in Java for the search term.
 * Upper-case letters are listed too, so the result does not depend on how the
 * database lower-cases non-ASCII letters.
 */
public final class SearchFolding {

    static final String ACCENTED =
            "ąćęłńóśźżĄĆĘŁŃÓŚŹŻ"
                    + "àáâãäåāăèéêëēěìíîïīòôõöøōőùúûüūůűýÿçčďğľĺňřšťžñ"
                    + "ÀÁÂÃÄÅĀĂÈÉÊËĒĚÌÍÎÏĪÒÔÕÖØŌŐÙÚÛÜŪŮŰÝŸÇČĎĞĽĹŇŘŠŤŽÑ";
    static final String PLAIN =
            "acelnoszzacelnoszz"
                    + "aaaaaaaaeeeeeeiiiiiooooooouuuuuuuyyccdgllnrstzn"
                    + "aaaaaaaaeeeeeeiiiiiooooooouuuuuuuyyccdgllnrstzn";

    private SearchFolding() {}

    /** Lower-cases and folds a column in SQL. */
    public static Expression<String> fold(CriteriaBuilder cb, Expression<String> column) {
        return cb.function("translate", String.class, cb.lower(column), cb.literal(ACCENTED), cb.literal(PLAIN));
    }

    /** Folds an already lower-cased search term exactly as {@link #fold(CriteriaBuilder, Expression)} does. */
    public static String fold(String value) {
        StringBuilder folded = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            int index = ACCENTED.indexOf(character);
            folded.append(index < 0 ? character : PLAIN.charAt(index));
        }
        return folded.toString();
    }
}
