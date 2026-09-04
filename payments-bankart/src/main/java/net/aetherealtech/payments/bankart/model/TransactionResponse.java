package net.aetherealtech.payments.bankart.model;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * The gateway's synchronous answer to any transaction call.
 *
 * <p>One shape covers every operation — the docs publish the same response table under debit,
 * capture, void, refund, register, deregister and payout alike.
 *
 * <p>For a redirect flow this is not the outcome. The docs are emphatic: "For the final result you
 * should only trust the notification, NOT the back redirection."
 *
 * @param extraData    string-to-string pairs; {@code remainingAmount} appears here after a partial
 *                     capture or refund
 * @param scheduleData present when the request started a schedule, or when this charge belongs to one
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionResponse(
        boolean success,
        String uuid,
        String purchaseId,
        ReturnType returnType,
        RedirectType redirectType,
        String redirectUrl,
        String redirectQRCode,
        String htmlContent,
        String paymentDescriptor,
        String paymentMethod,
        @JsonDeserialize(using = ReturnDataDeserializer.class) ReturnData returnData,
        ScheduleData scheduleData,
        Map<String, Object> extraData,
        List<TransactionError> errors) {

    public TransactionResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** The instrument as a card, when that is what it was. Empty for iban, phone, wallet or nothing. */
    public Optional<CardData> cardData() {
        return returnData instanceof CardData card ? Optional.of(card) : Optional.empty();
    }

    public boolean isError() {
        return returnType == ReturnType.ERROR || !success;
    }

    public boolean isRedirect() {
        return returnType == ReturnType.REDIRECT;
    }

    public Optional<String> remainingAmount() {
        return Optional.ofNullable(extraData)
                .map(data -> data.get("remainingAmount"))
                .map(Object::toString);
    }
}
