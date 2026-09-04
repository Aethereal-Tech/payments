package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The state of a schedule, as {@code oldStatus} and {@code newStatus} report it.
 *
 * <p>{@link #NON_EXISTING} is not in the specification's {@code ScheduleStatus} enum. It is sent
 * anyway: the spec's own {@code /schedule/{apiKey}/start} success example answers
 * {@code oldStatus: NON-EXISTING}, which is the only sane thing to say about a schedule that did not
 * exist a moment ago. It is modelled as a real value rather than degraded to {@link #UNKNOWN}
 * because it means something specific, and a caller reading "unknown" would have no way to tell
 * "the schedule was just created" from "the gateway said something we have never heard of".
 */
public enum ScheduleStatus {

    ACTIVE("ACTIVE"),
    PAUSED("PAUSED"),
    CANCELLED("CANCELLED"),
    ERROR("ERROR"),
    CREATE_PENDING("CREATE-PENDING"),
    NON_EXISTING("NON-EXISTING"),
    UNKNOWN("");

    private final String wire;

    ScheduleStatus(String wire) {
        this.wire = wire;
    }

    @JsonCreator
    public static ScheduleStatus fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (ScheduleStatus status : values()) {
            if (status != UNKNOWN && status.wire.equalsIgnoreCase(value)) {
                return status;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String wireValue() {
        return wire;
    }
}
