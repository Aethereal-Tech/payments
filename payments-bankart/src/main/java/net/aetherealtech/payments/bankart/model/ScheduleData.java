package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The schedule a transaction belongs to, echoed on a transaction response and on a notification.
 *
 * <p>Its presence is the ONLY way to tell that a callback describes a recurring charge rather than a
 * one-off: {@code transactionType} has no {@code SCHEDULE} value, so a schedule's own charge arrives
 * as an ordinary {@code DEBIT}.
 *
 * @param scheduledAt a {@code DateTimeZone}, i.e. {@code 2019-09-30T12:00:00+00:00} — when the next
 *                    charge is due
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleData(
        String scheduleId,
        ScheduleStatus scheduleStatus,
        String scheduledAt,
        String merchantMetaData) {
}
