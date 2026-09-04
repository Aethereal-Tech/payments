package net.aetherealtech.payments.agentaos;

/**
 * Which header carries the credential, decided by the shape of the key itself.
 *
 * <p>AgentaOS issues two kinds. A dashboard API key goes in a header of its own; the CLI's session
 * token is a JWT and goes in {@code authorization}. The SDK detects which from the string rather than
 * asking, and so does this — a caller pasting a key from one place or the other should not also have to
 * know which header it belongs in.
 */
public enum AuthMode {

    /** A dashboard key, {@code sk_live_…} or {@code sk_test_…}, sent as {@code x-api-key}. */
    API_KEY("x-api-key"),

    /** A three-part dotted session token, sent as {@code authorization: Bearer …}. */
    JWT("authorization");

    private final String headerName;

    AuthMode(final String headerName) {
        this.headerName = headerName;
    }

    /** The header this mode's credential travels in. */
    public String headerName() {
        return headerName;
    }

    /** The header value for {@code key}: the key itself, or {@code Bearer } and the token. */
    public String headerValue(final String key) {
        return this == JWT ? "Bearer " + key : key;
    }
}
