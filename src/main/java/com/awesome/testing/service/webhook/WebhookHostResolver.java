package com.awesome.testing.service.webhook;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Resolves webhook host names; a bean so tests can stub DNS (for example to simulate rebinding). */
@FunctionalInterface
public interface WebhookHostResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;

    static WebhookHostResolver system() {
        return host -> List.of(InetAddress.getAllByName(host));
    }
}
