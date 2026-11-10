package com.awesome.testing.service.webhook;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pinned HTTP client against a real HTTP server on localhost. */
class WebhookHttpSenderTest {

    private static final WireMockServer SERVER = new WireMockServer(options().dynamicPort().dynamicHttpsPort());
    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    private final WebhookHttpSender sender = new WebhookHttpSender();

    @BeforeAll
    static void start() {
        SERVER.start();
    }

    @AfterAll
    static void stop() {
        SERVER.stop();
    }

    @BeforeEach
    void reset() {
        SERVER.resetAll();
    }

    @Test
    void postsTheBodyToThePinnedAddressWithTheOriginalHostHeader() throws IOException {
        // given
        SERVER.stubFor(post(urlEqualTo("/hook?source=shop")).willReturn(aResponse().withStatus(202).withBody("accepted")));
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Webhook-Event", "order.created");

        // when
        WebhookHttpSender.Response response = sender.post(target("http", "hooks.example.test", SERVER.port(),
                "/hook?source=shop"), headers, "{\"a\":\"żółw\"}", Duration.ofSeconds(5));

        // then
        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEqualTo("accepted");
        SERVER.verify(postRequestedFor(urlEqualTo("/hook?source=shop"))
                .withHeader("Host", equalTo("hooks.example.test:" + SERVER.port()))
                .withHeader("X-Webhook-Event", equalTo("order.created"))
                .withHeader("Content-Length", equalTo(String.valueOf("{\"a\":\"żółw\"}".getBytes(StandardCharsets.UTF_8).length)))
                .withRequestBody(equalToJson("{\"a\":\"żółw\"}")));
    }

    @Test
    void returnsRedirectsWithoutFollowingThem() throws IOException {
        // given
        SERVER.stubFor(post(urlEqualTo("/hook")).willReturn(aResponse().withStatus(302)
                .withHeader("Location", "http://169.254.169.254/latest/meta-data/")));

        // when
        WebhookHttpSender.Response response = sender.post(target("http", "hooks.example.test", SERVER.port(), "/hook"),
                Map.of(), "{}", Duration.ofSeconds(5));

        // then
        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(SERVER.getAllServeEvents()).hasSize(1);
    }

    @Test
    void readsAtMostFourKilobytesOfTheBody() throws IOException {
        // given
        SERVER.stubFor(post(urlEqualTo("/big")).willReturn(aResponse().withStatus(500).withBody("x".repeat(100_000))));

        // when
        WebhookHttpSender.Response response = sender.post(target("http", "hooks.example.test", SERVER.port(), "/big"),
                Map.of(), "{}", Duration.ofSeconds(5));

        // then
        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.body()).hasSize(WebhookHttpSender.MAX_BODY_BYTES);
    }

    @Test
    void decodesChunkedBodies() throws IOException {
        // given
        SERVER.stubFor(post(urlEqualTo("/chunked")).willReturn(aResponse().withStatus(200)
                .withChunkedDribbleDelay(3, 30).withBody("hello webhook")));

        // when
        WebhookHttpSender.Response response = sender.post(target("http", "hooks.example.test", SERVER.port(), "/chunked"),
                Map.of(), "{}", Duration.ofSeconds(5));

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("hello webhook");
    }

    @Test
    void timesOutWhenTheReceiverIsSlowerThanTheDeadline() {
        // given
        SERVER.stubFor(post(urlEqualTo("/slow")).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        // when / then
        assertThatThrownBy(() -> sender.post(target("http", "hooks.example.test", SERVER.port(), "/slow"),
                Map.of(), "{}", Duration.ofMillis(300)))
                .isInstanceOf(SocketTimeoutException.class);
    }

    @Test
    void verifiesTlsCertificatesAndHostNames() {
        // given: WireMock's self-signed certificate is not trusted
        SERVER.stubFor(post(urlEqualTo("/tls")).willReturn(aResponse().withStatus(200)));

        // when / then
        assertThatThrownBy(() -> sender.post(target("https", "hooks.example.test", SERVER.httpsPort(), "/tls"),
                Map.of(), "{}", Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class);
        assertThat(SERVER.getAllServeEvents()).isEmpty();
    }

    @Test
    void failsWhenNothingListens() throws IOException {
        // given
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        // when / then
        assertThatThrownBy(() -> sender.post(target("http", "hooks.example.test", closedPort, "/"), Map.of(), "{}",
                Duration.ofSeconds(2))).isInstanceOf(IOException.class);
    }

    @Test
    void writesAMinimalRequestHead() {
        // given
        WebhookUrlPolicy.Target target = new WebhookUrlPolicy.Target(URI.create("https://hooks.example.com"), true,
                "hooks.example.com", 443, List.of(LOOPBACK));

        // when
        String head = WebhookHttpSender.requestHead(target, Map.of("User-Agent", "AwesomeTesting-Webhooks/1.0"), 12);

        // then
        assertThat(head).isEqualTo("POST / HTTP/1.1\r\nHost: hooks.example.com\r\n"
                + "User-Agent: AwesomeTesting-Webhooks/1.0\r\nContent-Length: 12\r\nConnection: close\r\n\r\n");
    }

    @Test
    void bracketsIpv6HostHeaders() {
        WebhookUrlPolicy.Target target = new WebhookUrlPolicy.Target(URI.create("http://[2606:4700::1111]:8080/x"), false,
                "2606:4700::1111", 8080, List.of(LOOPBACK));
        assertThat(WebhookHttpSender.requestHead(target, Map.of(), 0)).startsWith("POST /x HTTP/1.1\r\nHost: [2606:4700::1111]:8080\r\n");
    }

    @Test
    void parsesStatusLines() throws IOException {
        assertThat(WebhookHttpSender.statusCode("HTTP/1.1 204 No Content\r\n\r\n")).isEqualTo(204);
        assertThat(WebhookHttpSender.statusCode("HTTP/1.0 599\r\n\r\n")).isEqualTo(599);
        assertThatThrownBy(() -> WebhookHttpSender.statusCode("HTTP/2 200\r\n")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> WebhookHttpSender.statusCode("HTTP/1.1 OK\r\n")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> WebhookHttpSender.statusCode("HTTP/1.1 600 Odd\r\n")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> WebhookHttpSender.statusCode("HTTP/1.1 99 Odd\r\n")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> WebhookHttpSender.statusCode("garbage")).isInstanceOf(IOException.class);
    }

    @Test
    void dechunksTruncatedBodiesBestEffort() {
        assertThat(new String(WebhookHttpSender.dechunk("5\r\nhello\r\n6;ext=1\r\n world\r\n0\r\n\r\n"
                .getBytes(StandardCharsets.ISO_8859_1)), StandardCharsets.ISO_8859_1)).isEqualTo("hello world");
        assertThat(new String(WebhookHttpSender.dechunk("a\r\nhel".getBytes(StandardCharsets.ISO_8859_1)),
                StandardCharsets.ISO_8859_1)).isEqualTo("hel");
        assertThat(new String(WebhookHttpSender.dechunk("zz\r\nraw".getBytes(StandardCharsets.ISO_8859_1)),
                StandardCharsets.ISO_8859_1)).isEqualTo("zz\r\nraw");
    }

    private static WebhookUrlPolicy.Target target(String scheme, String host, int port, String pathAndQuery) {
        return new WebhookUrlPolicy.Target(URI.create(scheme + "://" + host + ":" + port + pathAndQuery),
                scheme.equals("https"), host, port, List.of(LOOPBACK));
    }
}
