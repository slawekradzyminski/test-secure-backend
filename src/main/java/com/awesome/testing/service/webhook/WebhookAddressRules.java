package com.awesome.testing.service.webhook;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Classifies IP addresses a webhook must never reach: loopback, private, link-local
 * (including cloud metadata at 169.254.169.254), carrier-grade NAT, multicast, reserved
 * and unspecified ranges, IPv6 unique-local and site-local, and IPv4 addresses embedded in
 * IPv6 (mapped, compatible, NAT64 and 6to4), which are judged by their IPv4 part.
 */
final class WebhookAddressRules {

    private static final List<Range> IPV4 = List.of(
            Range.parse("0.0.0.0/8", "an unspecified address"),
            Range.parse("10.0.0.0/8", "a private address"),
            Range.parse("100.64.0.0/10", "a carrier-grade NAT address"),
            Range.parse("127.0.0.0/8", "a loopback address"),
            Range.parse("169.254.0.0/16", "a link-local address"),
            Range.parse("172.16.0.0/12", "a private address"),
            Range.parse("192.0.0.0/24", "a reserved address"),
            Range.parse("192.0.2.0/24", "a documentation address"),
            Range.parse("192.168.0.0/16", "a private address"),
            Range.parse("198.18.0.0/15", "a reserved address"),
            Range.parse("198.51.100.0/24", "a documentation address"),
            Range.parse("203.0.113.0/24", "a documentation address"),
            Range.parse("224.0.0.0/4", "a multicast address"),
            Range.parse("240.0.0.0/4", "a reserved address"));

    private static final List<Range> IPV6 = List.of(
            Range.parse("::/128", "an unspecified address"),
            Range.parse("::1/128", "a loopback address"),
            Range.parse("100::/64", "a reserved address"),
            Range.parse("64:ff9b:1::/48", "a reserved address"),
            Range.parse("2001::/32", "a reserved address"),
            Range.parse("2001:db8::/32", "a documentation address"),
            Range.parse("fc00::/7", "a unique-local address"),
            Range.parse("fe80::/10", "a link-local address"),
            Range.parse("fec0::/10", "a site-local address"),
            Range.parse("ff00::/8", "a multicast address"));

    /** Built from bytes: {@link InetAddress} turns IPv4-mapped literals into IPv4 addresses. */
    private static final Range IPV4_MAPPED = new Range(
            new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 0, 0, 0, 0}, 96, "");
    private static final Range IPV4_COMPATIBLE = Range.parse("::/96", "");
    private static final Range NAT64 = Range.parse("64:ff9b::/96", "");
    private static final Range SIX_TO_FOUR = Range.parse("2002::/16", "");

    private WebhookAddressRules() {
    }

    /** Why the address is not a public unicast address, or empty when it may be called. */
    static Optional<String> blockedReason(InetAddress address) {
        byte[] bytes = address.getAddress();
        return address instanceof Inet4Address ? ipv4Reason(bytes) : ipv6Reason(bytes);
    }

    private static Optional<String> ipv4Reason(byte[] bytes) {
        if (Arrays.equals(bytes, new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255})) {
            return Optional.of("a broadcast address");
        }
        return IPV4.stream().filter(range -> range.contains(bytes)).map(Range::reason).findFirst();
    }

    private static Optional<String> ipv6Reason(byte[] bytes) {
        Optional<String> direct = IPV6.stream().filter(range -> range.contains(bytes)).map(Range::reason).findFirst();
        if (direct.isPresent()) {
            return direct;
        }
        if (IPV4_MAPPED.contains(bytes) || IPV4_COMPATIBLE.contains(bytes) || NAT64.contains(bytes)) {
            return ipv4Reason(Arrays.copyOfRange(bytes, 12, 16));
        }
        if (SIX_TO_FOUR.contains(bytes)) {
            return ipv4Reason(Arrays.copyOfRange(bytes, 2, 6));
        }
        return Optional.empty();
    }

    private static InetAddress literal(String address) {
        try {
            return InetAddress.ofLiteral(address);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Invalid built-in range " + address, e);
        }
    }

    private record Range(byte[] network, int prefix, String reason) {

        static Range parse(String cidr, String reason) {
            String[] parts = cidr.split("/");
            return new Range(literal(parts[0]).getAddress(), Integer.parseInt(parts[1]), reason);
        }

        /** Only called with addresses of the range's family (the IPv4 and IPv6 lists are separate). */
        boolean contains(byte[] address) {
            int fullBytes = prefix / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (address[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefix % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = (0xFF << (8 - remainingBits)) & 0xFF;
            return (address[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
