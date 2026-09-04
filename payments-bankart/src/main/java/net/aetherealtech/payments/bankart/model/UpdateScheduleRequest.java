package net.aetherealtech.payments.bankart.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Changes a running schedule in place — its price, its cadence, or the instrument behind it.
 *
 * <p>Every field is optional, including {@code registrationUuid}: the docs' own example body is
 * {@code {"amount": "9.99"}} and nothing else. Whatever is absent is left as it was, which is what
 * makes this the only way to reprice a subscription without cancelling and recreating it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UpdateScheduleRequest(
        String registrationUuid,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        Integer periodLength,
        SchedulePeriodUnit periodUnit,
        String startDateTime,
        String merchantMetaData,
        String callbackUrl) {

    public UpdateScheduleRequest {
        AmountSerializer.requireValid(amount, "amount");
    }

    public static UpdateScheduleRequest ofAmount(BigDecimal amount) {
        return new UpdateScheduleRequest(null, amount, null, null, null, null, null, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String registrationUuid;
        private BigDecimal amount;
        private String currency;
        private Integer periodLength;
        private SchedulePeriodUnit periodUnit;
        private String startDateTime;
        private String merchantMetaData;
        private String callbackUrl;

        private Builder() {
        }

        public Builder registrationUuid(String v) { this.registrationUuid = v; return this; }
        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder periodLength(Integer v) { this.periodLength = v; return this; }
        public Builder periodUnit(SchedulePeriodUnit v) { this.periodUnit = v; return this; }
        public Builder startDateTime(String v) { this.startDateTime = v; return this; }
        public Builder merchantMetaData(String v) { this.merchantMetaData = v; return this; }
        public Builder callbackUrl(String v) { this.callbackUrl = v; return this; }

        public UpdateScheduleRequest build() {
            return new UpdateScheduleRequest(registrationUuid, amount, currency, periodLength, periodUnit,
                    startDateTime, merchantMetaData, callbackUrl);
        }
    }
}
