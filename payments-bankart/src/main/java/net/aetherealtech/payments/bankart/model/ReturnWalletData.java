package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The wallet variant of {@code returnData} — PayPal and the like.
 *
 * @param type the discriminator the gateway sends as {@code _TYPE}, i.e. {@code walletData}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnWalletData(
        @JsonProperty("_TYPE") String type,
        String walletReferenceId,
        String walletOwner,
        String walletType) implements ReturnData {
}
