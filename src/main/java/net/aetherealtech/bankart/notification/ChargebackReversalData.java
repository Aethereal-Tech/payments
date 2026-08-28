package net.aetherealtech.bankart.notification;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Details of a chargeback that was itself reversed, e.g. after a won dispute. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChargebackReversalData(
        String originalUuid,
        String originalMerchantTransactionId,
        String chargebackUuid,
        BigDecimal amount,
        String currency,
        String reason,
        String reversalDateTime) {
}
