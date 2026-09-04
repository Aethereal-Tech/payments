package net.aetherealtech.payments.bankart.model;

import java.io.IOException;
import java.math.BigDecimal;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * Writes monetary amounts as the JSON strings the API expects — "decimals separated by ., max. 3
 * decimals" — rather than as JSON numbers, which is what Jackson would do with a BigDecimal by
 * default.
 *
 * <p>{@code toPlainString} rather than {@code toString} so that a value carrying an exponent
 * (which {@code new BigDecimal("9.99E+2")} legitimately does) is never transmitted in scientific
 * notation.
 */
public final class AmountSerializer extends JsonSerializer<BigDecimal> {

    public static final int MAX_SCALE = 3;

    @Override
    public void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) throws IOException {
        generator.writeString(value.toPlainString());
    }

    /**
     * Rejects an over-precise amount at construction, where the stack trace still names the caller,
     * instead of letting the gateway reject it as a 1002 minutes later.
     */
    public static BigDecimal requireValid(BigDecimal amount, String field) {
        if (amount == null) {
            return null;
        }
        if (amount.scale() > MAX_SCALE) {
            throw new IllegalArgumentException(
                    field + " must have at most " + MAX_SCALE + " decimals, got " + amount.toPlainString());
        }
        return amount;
    }
}
