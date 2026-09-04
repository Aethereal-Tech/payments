package net.aetherealtech.payments.bankart.exception;

/**
 * A general gateway error: the request itself was refused rather than a payment declined.
 *
 * <p>The documented shape is {@code {"success": false, "errorMessage": ..., "errorCode": ...}}, and
 * covers authentication failures, a bad signature (1004), validation errors (1002) and rate limiting
 * (1009). The docs are explicit that {@code errorMessage} is free text that may change, so branch on
 * {@link #errorCode()} and never on the message.
 */
public class BankartApiException extends BankartException {

    private final int errorCode;
    private final int httpStatus;
    private final String rawBody;

    public BankartApiException(String message, int errorCode, int httpStatus, String rawBody) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.rawBody = rawBody;
    }

    /** The gateway's consolidated error code, or 0 when the response carried none. */
    public int errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }

    public String rawBody() {
        return rawBody;
    }
}
