package com.awesome.testing.internal.security;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which callers each internal path accepts. Rules are checked in the order
 * they were added and the first whose regular expression matches the whole
 * path (without the context path) wins; other internal paths accept the
 * fallback callers. Built once in {@link com.awesome.testing.internal.InternalApiConfig}.
 *
 * <pre>{@code
 * InternalRoutes.builder()
 *         .route("/internal/v1/orders/\\d+/payment", ServiceCaller.PAYMENT)
 *         .otherwise(ServiceCaller.WAREHOUSE)
 *         .build();
 * }</pre>
 */
public final class InternalRoutes {

    private final List<Rule> rules;
    private final Set<ServiceCaller> fallback;

    private InternalRoutes(List<Rule> rules, Set<ServiceCaller> fallback) {
        this.rules = List.copyOf(rules);
        this.fallback = Set.copyOf(fallback);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Callers accepted for {@code path}; empty only when the fallback is empty. */
    public Set<ServiceCaller> callersFor(String path) {
        for (Rule rule : rules) {
            if (rule.path().matcher(path).matches()) {
                return rule.callers();
            }
        }
        return fallback;
    }

    private record Rule(Pattern path, Set<ServiceCaller> callers) {
    }

    public static final class Builder {

        private final List<Rule> rules = new ArrayList<>();
        private Set<ServiceCaller> fallback = Set.of();

        private Builder() {
        }

        /** Paths matching {@code pathRegex} entirely accept only {@code callers}. */
        public Builder route(String pathRegex, ServiceCaller first, ServiceCaller... others) {
            rules.add(new Rule(Pattern.compile(pathRegex), Set.copyOf(EnumSet.of(first, others))));
            return this;
        }

        /** Callers accepted by internal paths that match no rule. */
        public Builder otherwise(ServiceCaller first, ServiceCaller... others) {
            fallback = EnumSet.of(first, others);
            return this;
        }

        public InternalRoutes build() {
            return new InternalRoutes(rules, fallback);
        }
    }
}
