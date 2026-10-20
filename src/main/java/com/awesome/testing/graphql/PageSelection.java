package com.awesome.testing.graphql;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;

/** GraphQL normalizes aliases, fragments and skip/include directives in the selection set. */
public record PageSelection(boolean items, boolean total, boolean orderItems) {
    public static PageSelection from(DataFetchingEnvironment environment) {
        DataFetchingFieldSelectionSet selection = environment.getSelectionSet();
        return new PageSelection(
                selection.contains("items"), selection.contains("total"), selection.contains("items/items"));
    }
}
