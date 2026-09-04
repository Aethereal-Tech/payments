package net.aetherealtech.payments.bankart.model;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Starts a schedule against an instrument that is already registered.
 *
 * <p>{@code registrationUuid} is the UUID of a register, debit-with-register or
 * preauthorize-with-register transaction, and is the only required field: amount, currency and
 * cadence are optional here because the gateway can take them from the registration, which is the
 * difference from the embedded {@link Schedule}.
 *
 * @param startDateTime a {@code DateTimeZone}, i.e. {@code 2019-09-30T01:00:00+00:00}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StartScheduleRequest(
        String registrationUuid,
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        Integer periodLength,
        SchedulePeriodUnit periodUnit,
        String startDateTime,
        String merchantMetaData,
        String callbackUrl) {

    public StartScheduleRequest {
        PaymentRequest.requireText(registrationUuid, "registrationUuid");
        AmountSerializer.requireValid(amount, "amount");
    }

    public static Builder builder(String registrationUuid) {
        return new Builder(registrationUuid);
    }

    public static final class Builder {
        private final String registrationUuid;
        private BigDecimal amount;
        private String currency;
        private Integer periodLength;
        private SchedulePeriodUnit periodUnit;
        private String startDateTime;
        private String merchantMetaData;
        private String callbackUrl;

        private Builder(String registrationUuid) {
            this.registrationUuid = registrationUuid;
        }

        public Builder amount(BigDecimal v) { this.amount = v; return this; }
        public Builder currency(String v) { this.currency = v; return this; }
        public Builder periodLength(Integer v) { this.periodLength = v; return this; }
        public Builder periodUnit(SchedulePeriodUnit v) { this.periodUnit = v; return this; }
        public Builder startDateTime(String v) { this.startDateTime = v; return this; }
        public Builder merchantMetaData(String v) { this.merchantMetaData = v; return this; }
        public Builder callbackUrl(String v) { this.callbackUrl = v; return this; }

        /** Amount and cadence together, which is how the docs' own example sends them. */
        public Builder every(int periodLength, SchedulePeriodUnit periodUnit, BigDecimal amount, String currency) {
            this.periodLength = periodLength;
            this.periodUnit = periodUnit;
            this.amount = amount;
            this.currency = currency;
            return this;
        }

        public StartScheduleRequest build() {
            return new StartScheduleRequest(registrationUuid, amount, currency, periodLength, periodUnit,
                    startDateTime, merchantMetaData, callbackUrl);
        }
    }
}
