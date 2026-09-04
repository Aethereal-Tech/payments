package net.aetherealtech.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** SEPA mandate details. {@code mandateDate} is YYYY-MM-DD. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IbanData(String iban, String bic, String mandateId, String mandateDate) {
}
