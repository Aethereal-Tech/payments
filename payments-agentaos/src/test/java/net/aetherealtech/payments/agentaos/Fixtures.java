package net.aetherealtech.payments.agentaos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the payloads under {@code src/test/resources/fixtures}, shaped like the SDK's own test data. */
final class Fixtures {

    private Fixtures() {
    }

    static String load(final String name) {
        try (InputStream in = Fixtures.class.getResourceAsStream("/fixtures/" + name)) {
            if (in == null) {
                throw new IllegalStateException("Missing fixture " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read fixture " + name, e);
        }
    }
}
