package com.awesome.testing.service.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DNS for webhook tests: host names registered here resolve to fixed answers, one per
 * lookup (the last answer repeats), which simulates DNS rebinding. IP literals resolve to
 * themselves and anything else is unknown, so tests never depend on real DNS.
 */
public class StubWebhookHostResolver implements WebhookHostResolver {

    private final Map<String, Deque<List<InetAddress>>> answers = new ConcurrentHashMap<>();

    /** Each lookup of {@code host} returns the next answer; the last one is repeated. */
    public void register(String host, String... addressesPerLookup) {
        Deque<List<InetAddress>> queue = new ArrayDeque<>();
        for (String answer : addressesPerLookup) {
            queue.add(java.util.Arrays.stream(answer.split(",")).map(InetAddress::ofLiteral).toList());
        }
        answers.put(host, queue);
    }

    @Override
    public synchronized List<InetAddress> resolve(String host) throws UnknownHostException {
        Deque<List<InetAddress>> queue = answers.get(host);
        if (queue != null) {
            return queue.size() > 1 ? queue.poll() : queue.peek();
        }
        try {
            return List.of(InetAddress.ofLiteral(host));
        } catch (IllegalArgumentException e) {
            throw new UnknownHostException(host);
        }
    }
}
