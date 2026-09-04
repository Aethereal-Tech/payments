package net.aetherealtech.payments.bankart.model;

import java.math.BigDecimal;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * A schedule started as part of a register, a debit-with-register or a preauthorize-with-register,
 * rather than through {@code /schedule/{apiKey}/start} afterwards.
 *
 * <p>Amount, currency, period length and period unit are all required here, which is the difference
 * from {@link StartScheduleRequest}: the standalone endpoint may inherit them from the registration,
 * the embedded object may not.
 *
 * @param periodLength the specification types this as an {@code integer} on the embedded object and
 *                     as a {@code number} on {@code StartSchedule} and {@code UpdateSchedule}, for
 *                     the same field with the same meaning. The narrower of the two is used in both
 *                     places, since a fractional period has no meaning the docs describe.
 * @param startDateTime a {@code DateTimeZone}, i.e. {@code 2019-09-30T01:00:00+00:00}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Schedule(
        @JsonSerialize(using = AmountSerializer.class) BigDecimal amount,
        String currency,
        Integer periodLength,
        SchedulePeriodUnit periodUnit,
        String startDateTime,
        String merchantMetaData,
        String callbackUrl) {

    public Schedule {
        Objects.requireNonNull(amount, "amount");
        PaymentRequest.requireText(currency, "currency");
        Objects.requireNonNull(periodLength, "periodLength");
        Objects.requireNonNull(periodUnit, "periodUnit");
        AmountSerializer.requireValid(amount, "amount");
        if (periodLength < 0) {
            throw new IllegalArgumentException("periodLength must not be negative");
        }
    }

    public static Schedule every(int periodLength, SchedulePeriodUnit periodUnit, BigDecimal amount, String currency) {
        return new Schedule(amount, currency, periodLength, periodUnit, null, null, null);
    }

    public Schedule startingAt(String startDateTime) {
        return new Schedule(amount, currency, periodLength, periodUnit, startDateTime, merchantMetaData, callbackUrl);
    }

    public Schedule withCallbackUrl(String callbackUrl) {
        return new Schedule(amount, currency, periodLength, periodUnit, startDateTime, merchantMetaData, callbackUrl);
    }

    public Schedule withMerchantMetaData(String merchantMetaData) {
        return new Schedule(amount, currency, periodLength, periodUnit, startDateTime, merchantMetaData, callbackUrl);
    }
}
