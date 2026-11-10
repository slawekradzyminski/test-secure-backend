package com.awesome.testing.service.webhook;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * SSRF guard for webhook targets. It is applied when a subscription is created or
 * updated and again right before every delivery, and the delivery connects to exactly
 * the addresses it vetted, so a DNS answer that changes between the checks (rebinding)
 * cannot redirect a delivery into the internal network.
 *
 * <p>A URL is accepted only when it is http(s) (http can be switched off), has no user
 * info, targets port 80, 443 or a configured extra port, names a host with at least one
 * dot that is not {@code localhost}, {@code *.localhost}, {@code *.local} or
 * {@code *.internal}, and every address the host resolves to is public
 * (see {@link WebhookAddressRules}).
 */
public class WebhookUrlPolicy {

    static final int MAX_URL_LENGTH = 2048;
    private static final Set<Integer> DEFAULT_PORTS = Set.of(80, 443);
    private static final List<String> BLOCKED_SUFFIXES = List.of(".localhost", ".local", ".internal");

    private final WebhookProperties properties;
    private final WebhookHostResolver resolver;
    private final boolean allowLoopback;

    /** A vetted target: connect to {@code addresses} and present {@code host} as Host and TLS SNI. */
    public record Target(URI uri, boolean https, String host, int port, List<InetAddress> addresses) {
    }

    public WebhookUrlPolicy(WebhookProperties properties, WebhookHostResolver resolver) {
        this(properties, resolver, false);
    }

    private WebhookUrlPolicy(WebhookProperties properties, WebhookHostResolver resolver, boolean allowLoopback) {
        this.properties = properties;
        this.resolver = resolver;
        this.allowLoopback = allowLoopback;
    }

    /**
     * Tests only: additionally accepts loopback addresses on any port, so a local WireMock
     * server can act as a webhook receiver. Every other rule still applies.
     */
    public static WebhookUrlPolicy allowingLoopbackForTests(WebhookProperties properties, WebhookHostResolver resolver) {
        return new WebhookUrlPolicy(properties, resolver, true);
    }

    public Target check(String url) throws WebhookUrlRejectedException {
        URI uri = parse(url);
        String scheme = Optional.ofNullable(uri.getScheme()).map(value -> value.toLowerCase(Locale.ROOT)).orElse("");
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new WebhookUrlRejectedException("URL must use http or https");
        }
        if (scheme.equals("http") && !properties.isAllowHttp()) {
            throw new WebhookUrlRejectedException("URL must use https");
        }
        if (uri.getRawUserInfo() != null || uri.getRawAuthority().contains("@")) {
            throw new WebhookUrlRejectedException("URL must not contain credentials");
        }
        String host = host(uri);
        boolean https = scheme.equals("https");
        int port = uri.getPort() == -1 ? (https ? 443 : 80) : uri.getPort();
        List<InetAddress> addresses = resolve(host);
        boolean loopbackOnly = allowLoopback && addresses.stream().allMatch(InetAddress::isLoopbackAddress);
        if (!loopbackOnly && !DEFAULT_PORTS.contains(port) && !properties.getAllowedPorts().contains(port)) {
            throw new WebhookUrlRejectedException("URL port " + port + " is not allowed; use 80 or 443");
        }
        for (InetAddress address : addresses) {
            if (allowLoopback && address.isLoopbackAddress()) {
                continue;
            }
            Optional<String> reason = WebhookAddressRules.blockedReason(address);
            if (reason.isPresent()) {
                throw new WebhookUrlRejectedException("Host " + host + " resolves to " + reason.get()
                        + "; webhooks can only call public addresses");
            }
        }
        return new Target(uri, https, host, port, addresses);
    }

    private static URI parse(String url) throws WebhookUrlRejectedException {
        if (url == null || url.isBlank()) {
            throw new WebhookUrlRejectedException("URL is required");
        }
        if (url.length() > MAX_URL_LENGTH) {
            throw new WebhookUrlRejectedException("URL must be at most " + MAX_URL_LENGTH + " characters");
        }
        try {
            URI uri = new URI(url.trim());
            if (!uri.isAbsolute() || uri.isOpaque() || uri.getRawAuthority() == null) {
                throw new WebhookUrlRejectedException("URL must be absolute, for example https://example.com/hook");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new WebhookUrlRejectedException("URL is not valid", e);
        }
    }

    private static String host(URI uri) throws WebhookUrlRejectedException {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new WebhookUrlRejectedException("URL must name a host");
        }
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) {
            return host.substring(1, host.length() - 1);
        }
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.equals("localhost") || BLOCKED_SUFFIXES.stream().anyMatch(host::endsWith)) {
            throw new WebhookUrlRejectedException("Host " + host + " is internal; webhooks can only call public hosts");
        }
        if (!host.contains(".")) {
            throw new WebhookUrlRejectedException(
                    "Host " + host + " is not a fully qualified domain name; webhooks can only call public hosts");
        }
        return host;
    }

    private List<InetAddress> resolve(String host) throws WebhookUrlRejectedException {
        List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException | SecurityException e) {
            throw new WebhookUrlRejectedException("Host " + host + " cannot be resolved", e);
        }
        if (addresses == null || addresses.isEmpty()) {
            throw new WebhookUrlRejectedException("Host " + host + " cannot be resolved");
        }
        return new ArrayList<>(addresses);
    }
}
