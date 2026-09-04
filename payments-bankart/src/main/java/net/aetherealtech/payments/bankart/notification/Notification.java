package net.aetherealtech.bankart.notification;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import net.aetherealtech.bankart.model.CardData;
import net.aetherealtech.bankart.model.CardDataDeserializer;
import net.aetherealtech.bankart.model.Customer;
import net.aetherealtech.bankart.model.TransactionType;

/**
 * An asynchronous status notification: the gateway's authoritative word on a transaction.
 *
 * <p>For anything involving a redirect this is the only trustworthy outcome — the customer's return
 * to your success URL is not one, and the docs say so outright.
 *
 * <p>Three properties of the delivery shape how it must be handled, all of them documented:
 * <ul>
 *   <li>It is retried until acknowledged, so the same notification arrives more than once.</li>
 *   <li>A transaction may go from failed to successful, so a later notification about a transaction
 *       you already settled is legitimate rather than a replay.</li>
 * </ul>
 * Both point the same way: process by {@code uuid} idempotently, and let the newest state win.
 *
 * @param code error code on a failed transaction. The docs' field table types it as a number while
 *             their own example sends {@code "2016"} as a string; both parse.
 * @param notificationSource set to {@code reconciliation} or {@code settlement} when an amount or
 *                           currency changed after the fact, in which case {@code originalAmount}
 *                           carries what it used to be
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Notification(
        NotificationResult result,
        String uuid,
        String merchantTransactionId,
        String purchaseId,
        TransactionType transactionType,
        String paymentMethod,
        BigDecimal amount,
        String currency,
        String message,
        Integer code,
        String adapterMessage,
        String adapterCode,
        String notificationSource,
        BigDecimal originalAmount,
        String originalCurrency,
        String merchantMetaData,
        Customer customer,
        @JsonDeserialize(using = CardDataDeserializer.class) CardData returnData,
        ChargebackData chargebackData,
        ChargebackReversalData chargebackReversalData,
        Map<String, Object> extraData) {

    /**
     * The body every notification must be answered with, verbatim. Paired with HTTP 200 it stops
     * the retry schedule; anything else and the gateway keeps re-sending.
     */
    public static final String ACKNOWLEDGEMENT = "OK";

    public boolean isSuccess() {
        return result == NotificationResult.OK;
    }

    public boolean isError() {
        return result == NotificationResult.ERROR;
    }

    /** Still in flight. A further notification will follow when it settles. */
    public boolean isPending() {
        return result == NotificationResult.PENDING;
    }

    public boolean isChargeback() {
        return transactionType == TransactionType.CHARGEBACK;
    }

    /** Present when the amount or currency was restated during reconciliation or settlement. */
    public Optional<String> restatedBy() {
        return Optional.ofNullable(notificationSource);
    }

    public Optional<String> extra(String key) {
        return Optional.ofNullable(extraData)
                .map(data -> data.get(key))
                .map(Object::toString);
    }
}
