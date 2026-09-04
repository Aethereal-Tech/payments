package net.aetherealtech.payments.bankart.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * How long one billing period of a schedule lasts, in units.
 *
 * <p>Four values and no more: there is no {@code HOUR}, and no unit finer than a day, so a cadence
 * shorter than daily cannot be expressed to this gateway at all.
 */
public enum SchedulePeriodUnit {

    DAY, WEEK, MONTH, YEAR, UNKNOWN;

    @JsonCreator
    public static SchedulePeriodUnit fromWire(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        for (SchedulePeriodUnit unit : values()) {
            if (unit != UNKNOWN && unit.name().equalsIgnoreCase(value)) {
                return unit;
            }
        }
        return UNKNOWN;
    }

    @JsonValue
    public String wireValue() {
        return name();
    }
}
