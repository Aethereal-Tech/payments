package net.aetherealtech.payments.bankart;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the payloads under {@code src/test/resources/fixtures}, each copied from the published docs. */
public final class Fixtures {

    private Fixtures() {
    }

    public static String load(String name) {
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
