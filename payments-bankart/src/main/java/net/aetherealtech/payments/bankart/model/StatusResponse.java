package net.aetherealtech.bankart.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * The answer to a status lookup by UUID or by merchant transaction ID.
 *
 * <p>This is the recovery path when a call times out: the transaction may exist even though no
 * response arrived, and asking by {@code merchantTransactionId} settles it without risking a double
 * charge.
 *
 * <p>{@code success} reports whether the <em>lookup</em> worked; {@code transactionStatus} reports
 * what happened to the money. A found-but-failed transaction is {@code success = true} with
 * {@code transactionStatus = ERROR}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StatusResponse(
        boolean success,
        String transactionStatus,
        String uuid,
        String referenceUuid,
        String merchantTransactionId,
        String purchaseId,
        TransactionType transactionType,
        String paymentMethod,
        BigDecimal amount,
        String currency,
        Customer customer,
        @JsonDeserialize(using = CardDataDeserializer.class) CardData returnData,
        Map<String, Object> extraData,
        String merchantMetaData,
        List<StatusError> errors) {

    public StatusResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
