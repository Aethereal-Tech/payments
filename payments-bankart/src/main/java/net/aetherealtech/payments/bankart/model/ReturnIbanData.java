package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The IBAN variant of {@code returnData} — SEPA direct debit, after the fact.
 *
 * <p>Deliberately not {@link IbanData}, which is the request side: what a merchant SENDS is four
 * fields with no discriminator, nested under {@code customer.paymentData.ibanData}; what comes BACK
 * is these eight, tagged {@code _TYPE}. Two schemas, two names — the specification keeps them
 * separate too ({@code IbanData} and {@code ReturnIbanData}).
 *
 * @param type        the discriminator the gateway sends as {@code _TYPE}, i.e. {@code ibanData}
 * @param mandateDate a {@code Date}, i.e. {@code 2019-09-30}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReturnIbanData(
        @JsonProperty("_TYPE") String type,
        String accountOwner,
        String iban,
        String bic,
        String mandateId,
        String mandateDate,
        String bankName,
        String bankBranchName,
        String country) implements ReturnData {
}
