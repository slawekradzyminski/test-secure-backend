package com.awesome.testing.service.webhook;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignerTest {

    private static final String BODY = "{\"id\":\"1\"}";
    private static final long TIMESTAMP = 1794346833L;

    @Test
    void signsTimestampDotBodyWithHmacSha256OfTheWholeSecret() {
        // given: reference value computed outside Java with
        // python3 -c 'hmac.new(b"whsec_test-secret", b"1794346833.{\"id\":\"1\"}", hashlib.sha256).hexdigest()'
        String expected = "32499550f1f454c098be0274368c71a768cbdb59856abbac0bf2cc8e592bc83b";

        // when
        String header = WebhookSigner.signatureHeader("whsec_test-secret", TIMESTAMP, BODY);

        // then
        assertThat(header).isEqualTo("t=1794346833,v1=" + expected);
        assertThat(WebhookSigner.signature("whsec_test-secret", TIMESTAMP, BODY)).isEqualTo(expected);
    }

    @Test
    void signatureDependsOnSecretTimestampAndBody() {
        // given
        String base = WebhookSigner.signature("whsec_test-secret", TIMESTAMP, BODY);

        // when / then
        assertThat(WebhookSigner.signature("whsec_other", TIMESTAMP, BODY))
                .isEqualTo("3f3bb00efefd7b744eabcf5c55971fcdefda132abbd8612666778f974a2f947b");
        assertThat(WebhookSigner.signature("whsec_test-secret", TIMESTAMP + 1, BODY)).isNotEqualTo(base);
        assertThat(WebhookSigner.signature("whsec_test-secret", TIMESTAMP, BODY + " ")).isNotEqualTo(base);
        assertThat(base).hasSize(64).matches("[0-9a-f]+");
    }
}
