package net.aetherealtech.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The card variant of {@code returnData}: what the gateway is willing to tell you about the
 * instrument after the fact.
 *
 * <p>{@code fingerprint} is the stable per-card identifier — the thing to use for "have we seen this
 * card before", since the PAN never reaches you.
 *
 * @param type the discriminator the gateway sends as {@code _TYPE}, e.g. {@code cardData}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CardData(
        @JsonProperty("_TYPE") String type,
        @JsonProperty("type") String brand,
        String cardHolder,
        String expiryMonth,
        String expiryYear,
        String binDigits,
        String firstSixDigits,
        String lastFourDigits,
        String fingerprint,
        String threeDSecure,
        String binBrand,
        String binBank,
        String binType,
        String binLevel,
        String binCountry) {
}
