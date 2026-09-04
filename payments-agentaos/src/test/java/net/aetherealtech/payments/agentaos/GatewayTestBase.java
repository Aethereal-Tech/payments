package net.aetherealtech.payments.agentaos;

import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * A client pointed at a local stub of the gateway.
 *
 * <p>Nothing here reaches {@code api.agentaos.ai}. A payments API has no consequence-free test path, and
 * a suite that needs credentials is a suite that quietly stops running.
 */
abstract class GatewayTestBase {

    protected static final String API_KEY = "sk_test_abc123";
    protected static final String JSON = "application/json";

    @RegisterExtension
    protected final WireMockExtension gateway = WireMockExtension.newInstance()
            .options(WireMockConfiguration.options().dynamicPort())
            .build();

    protected AgentaOsConfig config() {
        return AgentaOsConfig.of(URI.create(gateway.baseUrl()), API_KEY);
    }

    protected AgentaOsClient client() {
        return new AgentaOsClient(config());
    }

    protected void stubJson(final String path, final int status, final String body) {
        gateway.stubFor(any(urlEqualTo(path))
                .willReturn(aResponse().withStatus(status).withHeader("Content-Type", JSON).withBody(body)));
    }

    protected void stubGet(final String path, final String body) {
        gateway.stubFor(get(urlEqualTo(path))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", JSON).withBody(body)));
    }

    protected LoggedRequest onlyRequest() {
        final List<LoggedRequest> requests = gateway.findAll(anyRequestedFor(UrlPattern.ANY));
        assertThat(requests).hasSize(1);
        return requests.getFirst();
    }

    protected List<LoggedRequest> allRequests() {
        return gateway.findAll(anyRequestedFor(UrlPattern.ANY));
    }

    /** Every request carries the credential and asks for JSON; nothing carries a user-agent of ours. */
    protected void assertCommonHeaders(final LoggedRequest request) {
        assertThat(request.getHeader("x-api-key")).isEqualTo(API_KEY);
        assertThat(request.getHeader("accept")).isEqualTo(JSON);
        assertThat(request.getHeader("authorization")).isNull();
    }
}
