package com.awesome.testing.service.webhook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SSRF matrix: which URLs and resolved addresses a webhook may target. */
class WebhookUrlPolicyTest {

    private StubWebhookHostResolver dns;
    private WebhookProperties properties;
    private WebhookUrlPolicy policy;

    @BeforeEach
    void setUp() {
        dns = new StubWebhookHostResolver();
        properties = new WebhookProperties();
        policy = new WebhookUrlPolicy(properties, dns);
        dns.register("hooks.example.com", "93.184.215.14");
    }

    @Test
    void acceptsPublicHttpsAndHttpTargetsAndPinsTheResolvedAddress() throws Exception {
        // when
        WebhookUrlPolicy.Target https = policy.check("https://Hooks.Example.com./orders?x=1");
        WebhookUrlPolicy.Target http = policy.check("http://hooks.example.com:80/a");

        // then
        assertThat(https.https()).isTrue();
        assertThat(https.host()).isEqualTo("hooks.example.com");
        assertThat(https.port()).isEqualTo(443);
        assertThat(https.addresses()).containsExactly(InetAddress.ofLiteral("93.184.215.14"));
        assertThat(https.uri().getRawQuery()).isEqualTo("x=1");
        assertThat(http.https()).isFalse();
        assertThat(http.port()).isEqualTo(80);
    }

    @Test
    void acceptsPublicIpLiterals() throws Exception {
        assertThat(policy.check("https://8.8.8.8/hook").addresses()).containsExactly(InetAddress.ofLiteral("8.8.8.8"));
        assertThat(policy.check("https://[2606:4700:4700::1111]/hook").host()).isEqualTo("2606:4700:4700::1111");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "''|URL is required",
            "not a url|URL is not valid",
            "/relative/path|URL must be absolute",
            "mailto:someone@example.com|URL must be absolute",
            "ftp://hooks.example.com/x|URL must use http or https",
            "file:///etc/passwd|URL must be absolute",
            "gopher://hooks.example.com/|URL must use http or https",
            "https://user:pass@hooks.example.com/|URL must not contain credentials",
            "https://user@hooks.example.com/|URL must not contain credentials",
            "https://hooks.example.com:8080/|URL port 8080 is not allowed",
            "https://hooks.example.com:22/|URL port 22 is not allowed",
            "https://localhost/hook|Host localhost is internal",
            "https://LOCALHOST./hook|Host localhost is internal",
            "https://api.localhost/hook|Host api.localhost is internal",
            "https://printer.local/hook|Host printer.local is internal",
            "https://metadata.google.internal/|Host metadata.google.internal is internal",
            "http://backend:4001/|Host backend is not a fully qualified domain name",
            "http://postgres/|Host postgres is not a fully qualified domain name",
            "https://unknown.example.com/|Host unknown.example.com cannot be resolved"})
    void rejectsUnsafeUrlShapes(String url, String message) {
        assertThatThrownBy(() -> policy.check(url))
                .isInstanceOf(WebhookUrlRejectedException.class)
                .hasMessageStartingWith(message);
    }

    @Test
    void rejectsOverlongUrls() throws Exception {
        String prefix = "https://hooks.example.com/";
        String longest = prefix + "a".repeat(WebhookUrlPolicy.MAX_URL_LENGTH - prefix.length());
        assertThat(policy.check(longest).uri().toString()).hasSize(2048);
        assertThatThrownBy(() -> policy.check(longest + "a")).hasMessage("URL must be at most 2048 characters");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "127.0.0.1|a loopback address",
            "127.255.255.254|a loopback address",
            "10.0.0.1|a private address",
            "10.255.255.255|a private address",
            "172.16.0.1|a private address",
            "172.31.255.255|a private address",
            "192.168.1.1|a private address",
            "169.254.169.254|a link-local address",
            "169.254.0.1|a link-local address",
            "100.64.0.1|a carrier-grade NAT address",
            "100.127.255.255|a carrier-grade NAT address",
            "0.0.0.0|an unspecified address",
            "0.1.2.3|an unspecified address",
            "224.0.0.1|a multicast address",
            "239.255.255.250|a multicast address",
            "240.0.0.1|a reserved address",
            "255.255.255.255|a broadcast address",
            "192.0.0.8|a reserved address",
            "198.18.0.1|a reserved address",
            "192.0.2.10|a documentation address",
            "::1|a loopback address",
            "::|an unspecified address",
            "fc00::1|a unique-local address",
            "fd12:3456:789a::1|a unique-local address",
            "fe80::1|a link-local address",
            "fec0::1|a site-local address",
            "ff02::1|a multicast address",
            "2001:db8::1|a documentation address",
            "2001:0:4136:e378::1|a reserved address",
            "::ffff:127.0.0.1|a loopback address",
            "::ffff:10.0.0.1|a private address",
            "::ffff:169.254.169.254|a link-local address",
            "::127.0.0.1|a loopback address",
            "64:ff9b::a00:1|a private address",
            "64:ff9b::7f00:1|a loopback address",
            "2002:a9fe:a9fe::1|a link-local address",
            "2002:c0a8:0101::1|a private address"})
    void rejectsHostsResolvingToNonPublicAddresses(String address, String reason) {
        // given
        dns.register("evil.example.com", address);

        // when / then
        assertThatThrownBy(() -> policy.check("https://evil.example.com/hook"))
                .isInstanceOf(WebhookUrlRejectedException.class)
                .hasMessage("Host evil.example.com resolves to " + reason + "; webhooks can only call public addresses");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1/", "http://10.1.2.3/", "http://169.254.169.254/latest/meta-data/",
            "http://[::1]/", "http://[::ffff:127.0.0.1]/", "http://[fd00::1]/", "http://0.0.0.0/"})
    void rejectsNonPublicIpLiterals(String url) {
        assertThatThrownBy(() -> policy.check(url))
                .isInstanceOf(WebhookUrlRejectedException.class)
                .hasMessageContaining("webhooks can only call public addresses");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://2130706433/", "http://0x7f000001/", "http://127.1/", "http://017700000001/",
            "http://[::1%25lo0]/", "http://hooks.example.com@127.0.0.1/"})
    void rejectsObfuscatedLoopbackForms(String url) {
        assertThatThrownBy(() -> policy.check(url)).isInstanceOf(WebhookUrlRejectedException.class);
    }

    @Test
    void rejectsAHostWhenAnyResolvedAddressIsInternal() {
        // given
        dns.register("mixed.example.com", "93.184.215.14,10.0.0.7");

        // when / then
        assertThatThrownBy(() -> policy.check("https://mixed.example.com/"))
                .hasMessageContaining("a private address");
    }

    @ParameterizedTest
    @ValueSource(strings = {"100.63.255.255", "100.128.0.0", "172.15.255.255", "172.32.0.0", "169.253.255.255",
            "169.255.0.0", "11.0.0.1", "126.255.255.255", "128.0.0.1", "192.167.255.255", "192.169.0.0",
            "223.255.255.255", "1.1.1.1", "2606:4700::1111", "2a00:1450:4001::1", "fbff::1", "fe7f::1"})
    void acceptsNeighboursOfBlockedRanges(String address) throws Exception {
        // given
        dns.register("edge.example.com", address);

        // when / then
        assertThat(policy.check("https://edge.example.com/").addresses()).hasSize(1);
    }

    @Test
    void rejectsHttpWhenItIsSwitchedOff() {
        // given
        properties.setAllowHttp(false);

        // when / then
        assertThatThrownBy(() -> policy.check("http://hooks.example.com/")).hasMessage("URL must use https");
    }

    @Test
    void allowsConfiguredExtraPorts() throws Exception {
        // given
        properties.setAllowedPorts(List.of(8443));

        // when / then
        assertThat(policy.check("https://hooks.example.com:8443/").port()).isEqualTo(8443);
        assertThatThrownBy(() -> policy.check("https://hooks.example.com:8444/"))
                .hasMessageStartingWith("URL port 8444 is not allowed");
    }

    @Test
    void reChecksResolutionOnEveryCallSoRebindingIsCaught() throws Exception {
        // given: public at subscription time, private at delivery time
        dns.register("rebind.example.com", "93.184.215.14", "169.254.169.254");

        // when
        WebhookUrlPolicy.Target first = policy.check("https://rebind.example.com/");

        // then
        assertThat(first.addresses()).containsExactly(InetAddress.ofLiteral("93.184.215.14"));
        assertThatThrownBy(() -> policy.check("https://rebind.example.com/"))
                .hasMessageContaining("a link-local address");
    }

    @Test
    void loopbackExemptionForTestsAllowsOnlyLoopbackOnAnyPort() throws Exception {
        // given
        WebhookUrlPolicy testPolicy = WebhookUrlPolicy.allowingLoopbackForTests(properties, dns);

        // when / then
        assertThat(testPolicy.check("http://127.0.0.1:61234/hook").port()).isEqualTo(61234);
        assertThatThrownBy(() -> testPolicy.check("http://10.0.0.1/")).hasMessageContaining("a private address");
        assertThatThrownBy(() -> testPolicy.check("http://localhost:61234/")).hasMessageContaining("is internal");
        assertThatThrownBy(() -> testPolicy.check("https://hooks.example.com:61234/"))
                .hasMessageStartingWith("URL port 61234 is not allowed");
        assertThatThrownBy(() -> policy.check("http://127.0.0.1/")).hasMessageContaining("a loopback address");
    }

    @Test
    void unresolvableResultsAreRejected() {
        WebhookUrlPolicy empty = new WebhookUrlPolicy(properties, host -> List.of());
        WebhookUrlPolicy nulls = new WebhookUrlPolicy(properties, host -> null);
        assertThatThrownBy(() -> empty.check("https://a.example.com/")).hasMessage("Host a.example.com cannot be resolved");
        assertThatThrownBy(() -> nulls.check("https://a.example.com/")).hasMessage("Host a.example.com cannot be resolved");
    }
}
