package net.aetherealtech.bankart;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

import net.aetherealtech.bankart.signing.HmacSigner;

/**
 * A client pointed at a local stub of the gateway.
 *
 * <p>Everything is exercised against recorded wire shapes rather than the live gateway: a payments
 * API has no test path that is free of consequences, and a suite that needs credentials is a suite
 * that stops running.
 */
abstract class GatewayTestBase {

    protected static final String API_KEY = "my-api-key";
    protected static final String USERNAME = "anyApiUser";
    protected static final String PASSWORD = "myPassword";
    protected static final String SHARED_SECRET = "my-shared-secret";
    protected static final String CONTENT_TYPE = "application/json; charset=utf-8";

    @RegisterExtension
    protected final WireMockExtension gateway = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.options().dynamicPort())
            .build();

    protected BankartClient client() {
        return new BankartClient(signingConfig());
    }

    protected BankartClient unsignedClient() {
        return new BankartClient(BankartConfig.of(baseUrl(), API_KEY, USERNAME, PASSWORD));
    }

    protected BankartConfig signingConfig() {
        return BankartConfig.of(baseUrl(), API_KEY, USERNAME, PASSWORD).withSharedSecret(SHARED_SECRET);
    }

    protected URI baseUrl() {
        return URI.create(gateway.baseUrl() + "/api/v3");
    }

    protected String transactionPath(String operation) {
        return "/api/v3/transaction/" + API_KEY + "/" + operation;
    }

    protected void stubTransaction(String operation, String responseBody) {
        gateway.stubFor(post(urlEqualTo(transactionPath(operation)))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));
    }

    protected LoggedRequest onlyRequest() {
        List<LoggedRequest> requests = gateway.findAll(com.github.tomakehurst.wiremock.client.WireMock
                .anyRequestedFor(com.github.tomakehurst.wiremock.matching.UrlPattern.ANY));
        assertThat(requests).hasSize(1);
        return requests.get(0);
    }

    /** Basic auth exactly as the docs spell it out: base64 of {@code username:password}. */
    protected void assertBasicAuth(LoggedRequest request) {
        String expected = "Basic " + Base64.getEncoder()
                .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));

        assertThat(request.getHeader("Authorization")).isEqualTo(expected);
    }

    /**
     * Recomputes the signature from what was actually transmitted. This is the assertion that would
     * catch a body serialised one way and hashed another — the failure mode the gateway reports only
     * as "1004 Invalid signature".
     */
    protected void assertSignature(LoggedRequest request, String method, String path) {
        String date = request.getHeader("X-Date");
        assertThat(date).as("X-Date is always sent; the docs give it precedence over Date").isNotNull();
        assertThat(request.getHeader("Date")).as("Date carries the same value it was signed with").isEqualTo(date);

        String expected = new HmacSigner(SHARED_SECRET)
                .sign(method, request.getBody(), CONTENT_TYPE, date, path);

        assertThat(request.getHeader("X-Signature")).isEqualTo(expected);
        // Compared case-insensitively only because WireMock's Jetty canonicalises the charset token
        // to "UTF-8" on receipt. A raw-socket probe confirms java.net.http transmits the header
        // byte-for-byte as set, which is what the gateway will hash.
        assertThat(request.getHeader("Content-Type")).isEqualToIgnoringCase(CONTENT_TYPE);
    }
}
