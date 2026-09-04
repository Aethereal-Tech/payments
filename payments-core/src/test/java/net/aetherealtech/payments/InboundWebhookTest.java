package net.aetherealtech.payments;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InboundWebhookTest {

    @Test
    void headerLookupIsCaseInsensitiveWhenStoredUpperAndAskedLower() {
        final InboundWebhook webhook = InboundWebhook.post(
                new byte[0], "/webhook", Map.of("X-Signature", "abc123"));

        assertThat(webhook.header("x-signature")).contains("abc123");
    }

    @Test
    void headerLookupIsCaseInsensitiveWhenStoredLowerAndAskedUpper() {
        final InboundWebhook webhook = InboundWebhook.post(
                new byte[0], "/webhook", Map.of("x-signature", "abc123"));

        assertThat(webhook.header("X-Signature")).contains("abc123");
    }

    @Test
    void anAbsentHeaderIsEmptyNotNull() {
        final InboundWebhook webhook = InboundWebhook.post(new byte[0], "/webhook", Map.of());

        assertThat(webhook.header("X-Missing")).isEmpty();
    }

    @Test
    void aHeaderPresentWithANullValueIsTreatedAsAbsent() {
        final Map<String, String> headers = new HashMap<>();
        headers.put("X-Signature", null);
        final InboundWebhook webhook = InboundWebhook.post(new byte[0], "/webhook", headers);

        assertThat(webhook.header("X-Signature")).isEmpty();
    }

    @Test
    void aNullHeaderNameInTheSourceMapIsDropped() {
        final Map<String, String> headers = new HashMap<>();
        headers.put(null, "orphan");
        headers.put("X-Signature", "abc123");
        final InboundWebhook webhook = InboundWebhook.post(new byte[0], "/webhook", headers);

        assertThat(webhook.headers()).containsOnlyKeys("X-Signature");
    }

    @Test
    void aNullHeadersMapIsAcceptedAsEmpty() {
        final InboundWebhook webhook = new InboundWebhook("POST", new byte[0], "/webhook", null);

        assertThat(webhook.headers()).isEmpty();
        assertThat(webhook.header("X-Signature")).isEmpty();
    }

    @Test
    void theBodyIsCopiedOnTheWayIn() {
        final byte[] source = "original".getBytes(StandardCharsets.UTF_8);
        final InboundWebhook webhook = InboundWebhook.post(source, "/webhook", Map.of());

        source[0] = 'X';

        assertThat(webhook.body()).isEqualTo("original".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void theBodyIsCopiedOnTheWayOut() {
        final InboundWebhook webhook = InboundWebhook.post(
                "original".getBytes(StandardCharsets.UTF_8), "/webhook", Map.of());

        final byte[] returned = webhook.body();
        returned[0] = 'X';

        assertThat(webhook.body()).isEqualTo("original".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void bodyAsStringDecodesUtf8IncludingNonAscii() {
        final String text = "плаќање €";
        final InboundWebhook webhook = InboundWebhook.post(
                text.getBytes(StandardCharsets.UTF_8), "/webhook", Map.of());

        assertThat(webhook.bodyAsString()).isEqualTo(text);
    }

    @Test
    void postAlwaysUsesThePostMethod() {
        final InboundWebhook webhook = InboundWebhook.post(new byte[0], "/webhook", Map.of());

        assertThat(webhook.method()).isEqualTo("POST");
    }

    @Test
    void toStringDoesNotContainTheBody() {
        final String secret = "card-number-4111111111111111";
        final byte[] body = secret.getBytes(StandardCharsets.UTF_8);
        final InboundWebhook webhook = InboundWebhook.post(body, "/webhook", Map.of("X-Signature", "abc"));

        final String rendered = webhook.toString();

        assertThat(rendered)
                .doesNotContain(secret)
                .contains("method=POST")
                .contains("bodyBytes=" + body.length);
    }
}
