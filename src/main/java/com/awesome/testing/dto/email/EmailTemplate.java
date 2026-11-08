package com.awesome.testing.dto.email;

public enum EmailTemplate {
    GENERIC,
    PASSWORD_RESET_REQUESTED,
    PASSWORD_RESET_CONFIRMED,
    BACK_IN_STOCK,
    ORDER_SHIPPED,
    ORDER_DELIVERED,
    RETURN_UPDATED;

    /**
     * Templates the JMS email consumer knows how to render. Other templates are
     * rendered by the backend and published as {@link #GENERIC} so older
     * consumers never receive an enum value they cannot deserialize.
     */
    public boolean isRenderedByConsumer() {
        return this == GENERIC || this == PASSWORD_RESET_REQUESTED || this == PASSWORD_RESET_CONFIRMED;
    }
}
