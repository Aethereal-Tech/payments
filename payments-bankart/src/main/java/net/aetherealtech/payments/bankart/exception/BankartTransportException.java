package net.aetherealtech.bankart.exception;

/**
 * The request never produced a gateway verdict — connection failure, timeout, or interruption.
 *
 * <p>The transaction's outcome is genuinely unknown here, which is not the same as failed. The
 * gateway may still have processed it, so the recovery is a status lookup by
 * {@code merchantTransactionId}, not a blind retry of the payment.
 */
public class BankartTransportException extends BankartException {

    public BankartTransportException(String message, Throwable cause) {
        super(message, cause);
    }
}
