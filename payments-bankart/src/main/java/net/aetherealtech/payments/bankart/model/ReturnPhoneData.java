package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The phone variant of {@code returnData} — a carrier-billed payment.
 *
 * @param type the discriminator the gateway sends as {@code _TYPE}, i.e. {@code phoneData}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnPhoneData(
        @JsonProperty("_TYPE") String type,
        String phoneNumber,
        String country,
        String operator) implements ReturnData {
}
