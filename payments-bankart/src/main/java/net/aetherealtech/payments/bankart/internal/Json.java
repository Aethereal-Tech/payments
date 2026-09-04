package net.aetherealtech.payments.bankart.internal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * The one mapper configuration this library uses, in one place, so that a body signed by
 * {@code BankartClient} and a body parsed by {@code NotificationParser} cannot drift apart.
 *
 * <p>Unknown properties are ignored by policy, not by oversight: the docs reserve the right to add
 * fields to responses and postbacks at any time, and a client that throws on one would break on a
 * gateway upgrade it had no say in.
 */
public final class Json {

    private Json() {
    }

    public static ObjectMapper mapper() {
        return JsonMapper.builder()
                .defaultPropertyInclusion(JsonInclude.Value.construct(
                        JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL))
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }
}
