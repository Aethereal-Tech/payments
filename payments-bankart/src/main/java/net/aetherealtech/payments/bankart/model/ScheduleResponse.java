package net.aetherealtech.payments.bankart.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The answer to every one of the six schedule operations — start, update, get, pause, continue and
 * cancel all share this one shape.
 *
 * <p>Three of its seven fields are here in defiance of the declared schema, which is worth stating
 * because a stricter reading would drop exactly the information a failure needs:
 *
 * <ul>
 *   <li>{@code errorMessage} and {@code errorCode} are not declared on {@code ScheduleResponse}, and
 *       the schema closes itself with {@code additionalProperties: false} — yet every error example
 *       in the specification sends both ({@code 7040} "The scheduleId is not valid or does not match
 *       to the connector", {@code 7070} "The status of the schedule is not valid for the requested
 *       operation"). The examples are the reproducible behaviour, so they win.</li>
 *   <li>{@code registrationUuid} is likewise undeclared: the schema names the field
 *       {@code registrationId}, and every example sends {@code registrationUuid}. Both are read, and
 *       {@link #registration()} answers with whichever arrived, so a caller never has to know which
 *       spelling its connector uses.</li>
 * </ul>
 *
 * @param oldStatus   the status before the operation; {@code NON-EXISTING} on a freshly started schedule
 * @param newStatus   the status after it — this is what to act on
 * @param scheduledAt a {@code DateTimeZone}, i.e. {@code 2019-09-30T12:00:00+00:00}: when the next
 *                    charge is due. Absent on pause and cancel, where there is no next charge.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScheduleResponse(
        boolean success,
        String scheduleId,
        String registrationId,
        String registrationUuid,
        ScheduleStatus oldStatus,
        ScheduleStatus newStatus,
        String scheduledAt,
        String merchantMetaData,
        String errorMessage,
        Integer errorCode) {

    /** The registration this schedule bills, under whichever of the two spellings arrived. */
    public Optional<String> registration() {
        return Optional.ofNullable(registrationUuid != null ? registrationUuid : registrationId);
    }

    public boolean isError() {
        return !success;
    }
}
