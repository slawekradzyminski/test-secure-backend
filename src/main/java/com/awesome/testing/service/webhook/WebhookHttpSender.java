package com.awesome.testing.service.webhook;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal HTTP/1.1 POST over a socket connected to an address the {@link WebhookUrlPolicy}
 * vetted, presenting the original host name in {@code Host} and TLS SNI (with normal
 * certificate and host name verification). The JDK {@code HttpClient} resolves host names
 * itself, so it cannot be pinned to a vetted address; this client can, and needs no
 * reflection in the native image.
 *
 * <p>Redirects are never followed, the whole exchange shares one deadline, response
 * headers are capped at 8 KB and at most 4 KB of the body is read.
 */
public class WebhookHttpSender {

    static final int MAX_HEADER_BYTES = 8 * 1024;
    static final int MAX_BODY_BYTES = 4 * 1024;
    private static final int MAX_INFORMATIONAL_RESPONSES = 5;

    private final SSLSocketFactory sslSocketFactory;

    /** The status and the start of the body of a response. */
    public record Response(int statusCode, String body) {
    }

    public WebhookHttpSender() {
        this((SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    WebhookHttpSender(SSLSocketFactory sslSocketFactory) {
        this.sslSocketFactory = sslSocketFactory;
    }

    public Response post(WebhookUrlPolicy.Target target, Map<String, String> headers, String body, Duration timeout)
            throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = open(target, deadline)) {
            socket.setSoTimeout(remainingMillis(deadline));
            // Closed together with the socket by the try-with-resources above.
            @SuppressWarnings("PMD.CloseResource")
            OutputStream out = socket.getOutputStream();
            out.write(requestHead(target, headers, payload.length).getBytes(StandardCharsets.ISO_8859_1));
            out.write(payload);
            out.flush();
            return readResponse(socket, socket.getInputStream(), deadline);
        }
    }

    private Socket open(WebhookUrlPolicy.Target target, long deadline) throws IOException {
        Socket plain = connect(target.addresses(), target.port(), deadline);
        if (!target.https()) {
            return plain;
        }
        try {
            plain.setSoTimeout(remainingMillis(deadline));
            SSLSocket tls = (SSLSocket) sslSocketFactory.createSocket(plain, target.host(), target.port(), true);
            SSLParameters parameters = tls.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            if (!isIpLiteral(target.host())) {
                parameters.setServerNames(List.of(new SNIHostName(target.host())));
            }
            tls.setSSLParameters(parameters);
            tls.startHandshake();
            return tls;
        } catch (IOException | RuntimeException e) {
            plain.close();
            throw e;
        }
    }

    private static Socket connect(List<InetAddress> addresses, int port, long deadline) throws IOException {
        IOException last = null;
        for (InetAddress address : addresses) {
            Socket socket = new Socket(Proxy.NO_PROXY);
            try {
                socket.connect(new InetSocketAddress(address, port), remainingMillis(deadline));
                return socket;
            } catch (IOException e) {
                socket.close();
                last = e;
            }
        }
        throw last != null ? last : new IOException("No address to connect to");
    }

    static String requestHead(WebhookUrlPolicy.Target target, Map<String, String> headers, int contentLength) {
        StringBuilder head = new StringBuilder();
        head.append("POST ").append(requestTarget(target)).append(" HTTP/1.1\r\n");
        head.append("Host: ").append(hostHeader(target)).append("\r\n");
        headers.forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("Content-Length: ").append(contentLength).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        return head.toString();
    }

    private static String requestTarget(WebhookUrlPolicy.Target target) {
        String path = target.uri().getRawPath();
        String query = target.uri().getRawQuery();
        return (path == null || path.isEmpty() ? "/" : path) + (query == null ? "" : "?" + query);
    }

    private static String hostHeader(WebhookUrlPolicy.Target target) {
        String host = target.host().contains(":") ? "[" + target.host() + "]" : target.host();
        int defaultPort = target.https() ? 443 : 80;
        return target.port() == defaultPort ? host : host + ":" + target.port();
    }

    private static Response readResponse(Socket socket, InputStream in, long deadline) throws IOException {
        String head = readHead(socket, in, deadline);
        int status = statusCode(head);
        int informational = 0;
        while (status < 200) {
            informational++;
            if (informational > MAX_INFORMATIONAL_RESPONSES) {
                throw new IOException("Too many informational responses");
            }
            head = readHead(socket, in, deadline);
            status = statusCode(head);
        }
        Map<String, String> headers = headers(head);
        byte[] body = readBody(socket, in, deadline, contentLength(headers));
        boolean chunked = headers.getOrDefault("transfer-encoding", "").toLowerCase(Locale.ROOT).contains("chunked");
        return new Response(status, new String(chunked ? dechunk(body) : body, StandardCharsets.UTF_8));
    }

    private static String readHead(Socket socket, InputStream in, long deadline) throws IOException {
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int matched = 0;
        byte[] terminator = {'\r', '\n', '\r', '\n'};
        while (matched < terminator.length) {
            socket.setSoTimeout(remainingMillis(deadline));
            int next = in.read();
            if (next == -1) {
                throw new IOException("Connection closed before the response headers ended");
            }
            head.write(next);
            if (head.size() > MAX_HEADER_BYTES) {
                throw new IOException("Response headers exceed " + MAX_HEADER_BYTES + " bytes");
            }
            if (next == terminator[matched]) {
                matched++;
            } else {
                matched = next == '\r' ? 1 : 0;
            }
        }
        return head.toString(StandardCharsets.ISO_8859_1);
    }

    static int statusCode(String head) throws IOException {
        String statusLine = head.lines().findFirst().orElse("");
        String[] parts = statusLine.split(" ", 3);
        if (parts.length < 2 || !parts[0].startsWith("HTTP/1.")) {
            throw new IOException("Malformed HTTP status line");
        }
        try {
            int status = Integer.parseInt(parts[1]);
            if (status < 100 || status > 599) {
                throw new IOException("Malformed HTTP status code");
            }
            return status;
        } catch (NumberFormatException e) {
            throw new IOException("Malformed HTTP status code", e);
        }
    }

    private static Map<String, String> headers(String head) {
        Map<String, String> headers = new HashMap<>();
        head.lines().skip(1).forEach(line -> {
            int colon = line.indexOf(':');
            if (colon > 0) {
                headers.put(line.substring(0, colon).trim().toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
            }
        });
        return headers;
    }

    private static long contentLength(Map<String, String> headers) {
        try {
            return Long.parseLong(headers.getOrDefault("content-length", "-1"));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static byte[] readBody(Socket socket, InputStream in, long deadline, long contentLength) throws IOException {
        long limit = contentLength >= 0 ? Math.min(contentLength, MAX_BODY_BYTES) : MAX_BODY_BYTES;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        while (body.size() < limit) {
            int remaining = remainingMillisOrZero(deadline);
            if (remaining == 0) {
                break;
            }
            socket.setSoTimeout(remaining);
            int read;
            try {
                read = in.read(buffer, 0, (int) Math.min(buffer.length, limit - body.size()));
            } catch (SocketTimeoutException e) {
                break;
            }
            if (read == -1) {
                break;
            }
            body.write(buffer, 0, read);
        }
        return body.toByteArray();
    }

    /** Best-effort decoding of a (possibly truncated) chunked body, for the response snippet only. */
    static byte[] dechunk(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int position = 0;
        while (position < raw.length) {
            int lineEnd = indexOfCrLf(raw, position);
            if (lineEnd < 0) {
                break;
            }
            String sizeLine = new String(raw, position, lineEnd - position, StandardCharsets.ISO_8859_1);
            int extension = sizeLine.indexOf(';');
            int size;
            try {
                size = Integer.parseInt((extension >= 0 ? sizeLine.substring(0, extension) : sizeLine).trim(), 16);
            } catch (NumberFormatException e) {
                return raw;
            }
            if (size <= 0) {
                break;
            }
            int start = lineEnd + 2;
            int end = Math.min(raw.length, start + size);
            out.write(raw, start, end - start);
            position = end + 2;
        }
        return out.toByteArray();
    }

    private static int indexOfCrLf(byte[] raw, int from) {
        for (int i = from; i + 1 < raw.length; i++) {
            if (raw[i] == '\r' && raw[i + 1] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static int remainingMillis(long deadline) throws SocketTimeoutException {
        int remaining = remainingMillisOrZero(deadline);
        if (remaining == 0) {
            throw new SocketTimeoutException("Timed out");
        }
        return remaining;
    }

    private static int remainingMillisOrZero(long deadline) {
        long remaining = (deadline - System.nanoTime()) / 1_000_000;
        return remaining <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, remaining);
    }

    private static boolean isIpLiteral(String host) {
        return host.contains(":") || host.chars().allMatch(c -> Character.isDigit(c) || c == '.');
    }
}
