package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One entry of an options list: a value to send back to the gateway, and a label to show a customer. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Option(String key, String value) {
}
