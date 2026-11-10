package com.awesome.testing.service.checkout;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** All {@link CartSummaryContributor} beans in order; none are registered by default. */
@Component
public class CartSummaryContributors {

    private final List<CartSummaryContributor> contributors;

    @Autowired
    public CartSummaryContributors(ObjectProvider<CartSummaryContributor> contributors) {
        this(contributors.orderedStream().toList());
    }

    public CartSummaryContributors(List<CartSummaryContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    public static CartSummaryContributors none() {
        return new CartSummaryContributors(List.of());
    }

    public void contribute(CartSummaryContext context) {
        contributors.forEach(contributor -> contributor.contribute(context));
    }
}
