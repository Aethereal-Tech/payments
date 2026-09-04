package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 3-D Secure 2 request data.
 *
 * <p>Only the fields a caller commonly sets are modelled. The gateway fills the rest from the
 * transaction and customer data it already holds, and the docs list the remainder as optional
 * enrichment for the frictionless flow.
 *
 * @param threeDSecure whether authentication is attempted; serialised as {@code 3dsecure}, a name
 *                     no Java identifier can carry
 * @param channel      01 app-based, 02 browser, 03 3DS-requestor-initiated
 * @param authenticationIndicator 01 payment, 02 recurring, 03 installment, 04 add card,
 *                                05 maintain card, 06 EMV token ID&amp;V
 * @param cardholderAuthenticationMethod how the cardholder authenticated to the requestor (01..06)
 * @param cardholderAuthenticationDateTime UTC, formatted {@code yyyy-MM-dd HH:mm}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ThreeDSecureData(
        @JsonProperty("3dsecure") ThreeDSecureMode threeDSecure,
        String schemeId,
        String channel,
        String authenticationIndicator,
        String cardholderAuthenticationMethod,
        String cardholderAuthenticationDateTime) {

    public static ThreeDSecureData of(ThreeDSecureMode mode) {
        return new ThreeDSecureData(mode, null, null, null, null, null);
    }
}
