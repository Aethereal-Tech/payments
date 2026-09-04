package net.aetherealtech.bankart.exception;

/**
 * Base of every failure this library raises.
 *
 * <p>Unchecked, because at the call sites that matter — a checkout controller, a scheduled retry —
 * there is nothing useful to do with most of these but let them travel. The subtypes are what carry
 * the distinction that a caller can actually act on: retry it ({@link BankartTransportException}),
 * fix the call ({@link BankartApiException}), or tell the customer ({@link BankartTransactionException}).
 */
public class BankartException extends RuntimeException {

    public BankartException(String message) {
        super(message);
    }

    public BankartException(String message, Throwable cause) {
        super(message, cause);
    }
}
