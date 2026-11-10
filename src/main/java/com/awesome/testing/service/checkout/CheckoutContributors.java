package com.awesome.testing.service.checkout;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/** All {@link CheckoutContributor} beans in order; none are registered by default. */
@Component
public class CheckoutContributors {

    private final List<CheckoutContributor> contributors;

    @Autowired
    public CheckoutContributors(ObjectProvider<CheckoutContributor> contributors) {
        this(contributors.orderedStream().toList());
    }

    public CheckoutContributors(List<CheckoutContributor> contributors) {
        this.contributors = List.copyOf(contributors);
    }

    public static CheckoutContributors none() {
        return new CheckoutContributors(List.of());
    }

    public void contribute(CheckoutContext context) {
        contributors.forEach(contributor -> contributor.contribute(context));
    }
}
