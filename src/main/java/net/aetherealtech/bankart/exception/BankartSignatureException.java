package net.aetherealtech.bankart.exception;

/**
 * An inbound notification did not authenticate: the signature failed to match, or its {@code Date}
 * fell outside the accepted window.
 *
 * <p>Treat as hostile input, not as a transient fault. Nothing in the payload may be trusted, so the
 * transaction must not be advanced on the strength of it.
 */
public class BankartSignatureException extends BankartException {

    public BankartSignatureException(String message) {
        super(message);
    }
}
