package net.aetherealtech.bankart.exception;

/** A notification body could not be parsed into the documented payload. */
public class BankartNotificationException extends BankartException {

    public BankartNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
