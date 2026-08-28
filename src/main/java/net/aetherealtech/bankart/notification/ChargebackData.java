package net.aetherealtech.bankart.notification;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Details of a chargeback, present on a notification whose {@code transactionType} is CHARGEBACK.
 *
 * <p>{@code originalUuid} points at the transaction being reversed — that, not the notification's
 * own {@code uuid}, is what identifies the order that lost its money.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChargebackData(
        String originalUuid,
        String originalMerchantTransactionId,
        BigDecimal amount,
        String currency,
        String reason,
        String chargebackDateTime) {
}
