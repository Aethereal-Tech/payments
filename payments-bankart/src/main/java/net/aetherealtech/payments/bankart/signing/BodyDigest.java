package net.aetherealtech.payments.bankart.signing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The hash applied to the request body before it enters the signature message.
 *
 * <p>The API reference specifies SHA-512 and notes that "in previous versions this is MD5 instead".
 * MD5 is retained only because the notification chapter still refers to it; see the README's
 * "Open questions" section.
 */
public enum BodyDigest {

    SHA512("SHA-512"),
    /** @deprecated superseded by {@link #SHA512} in API v3; present for legacy notification handling. */
    @Deprecated
    MD5("MD5");

    private final String algorithm;

    BodyDigest(String algorithm) {
        this.algorithm = algorithm;
    }

    /** Lowercase hex, which is the form the documented worked example hashes to. */
    public String hashHex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(algorithm).digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(algorithm + " is required of every JRE", e);
        }
    }
}
