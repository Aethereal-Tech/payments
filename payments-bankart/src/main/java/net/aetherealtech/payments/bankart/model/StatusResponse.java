package net.aetherealtech.payments.bankart.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
 *
 * @param schedules the schedules this transaction belongs to, keyed by the gateway. A map rather
 *                  than the single {@code scheduleData} a transaction response and a notification
 *                  carry; the specification gives no meaning for the keys, so they are passed
 *                  through untouched.
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
        @JsonDeserialize(using = ReturnDataDeserializer.class) ReturnData returnData,
        Map<String, ScheduleData> schedules,
        PayByLinkData payByLinkData,
        Map<String, Object> extraData,
        String merchantMetaData,
        List<StatusError> errors) {

    public StatusResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
        schedules = schedules == null ? Map.of() : Map.copyOf(schedules);
    }

    /** The instrument as a card, when that is what it was. Empty for iban, phone, wallet or nothing. */
    public Optional<CardData> cardData() {
        return returnData instanceof CardData card ? Optional.of(card) : Optional.empty();
    }
}
