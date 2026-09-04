package net.aetherealtech.bankart.exception;

import java.util.List;
import java.util.Optional;

import net.aetherealtech.bankart.model.TransactionError;
import net.aetherealtech.bankart.model.TransactionResponse;

/**
 * The gateway accepted the request and answered {@code returnType = ERROR}: a transaction that
 * failed or was declined.
 *
 * <p>Distinct from {@link BankartApiException} because the caller's response differs — the request
 * was well-formed and there is a transaction on the gateway with a UUID, which the {@link #response()}
 * carries. Declines belong to the customer ("your card was declined"); API errors belong to the
 * integrator.
 */
public class BankartTransactionException extends BankartException {

    private final transient TransactionResponse response;

    public BankartTransactionException(TransactionResponse response) {
        super(describe(response));
        this.response = response;
    }

    public TransactionResponse response() {
        return response;
    }

    public List<TransactionError> errors() {
        return response.errors();
    }

    /**
     * The error to act on. The docs return an array without promising an order or a single element,
     * so the first is a convention of this library, not of the gateway.
     */
    public Optional<TransactionError> firstError() {
        return response.errors().stream().findFirst();
    }

    public String uuid() {
        return response.uuid();
    }

    private static String describe(TransactionResponse response) {
        return response.errors().stream()
                .findFirst()
                .map(e -> "Transaction " + response.uuid() + " failed: " + e.errorCode() + " " + e.errorMessage())
                .orElseGet(() -> "Transaction " + response.uuid() + " failed without a reported error");
    }
}
