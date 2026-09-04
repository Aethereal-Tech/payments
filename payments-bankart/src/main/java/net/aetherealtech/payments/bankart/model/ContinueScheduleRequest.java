package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Resumes a paused schedule, naming the date the next charge is to fall on.
 *
 * <p>{@code continueDateTime} is required, so resuming is always a decision about when — there is no
 * "carry on from wherever you left off".
 *
 * @param continueDateTime a {@code DateTimeZone}, i.e. {@code 2019-09-30T01:00:00+00:00}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContinueScheduleRequest(String continueDateTime) {

    public ContinueScheduleRequest {
        PaymentRequest.requireText(continueDateTime, "continueDateTime");
    }

    public static ContinueScheduleRequest at(String continueDateTime) {
        return new ContinueScheduleRequest(continueDateTime);
    }
}
