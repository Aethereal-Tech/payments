package net.aetherealtech.payments.bankart.model;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * An adapter's published list, from {@code POST /options/{apiKey}/{optionsName}}.
 *
 * <p>The failure field is read under two names because the schema and the example disagree: the
 * schema declares {@code error} (a string), while the endpoint's own error example sends
 * {@code errorMessage}. {@link #failure()} answers with whichever arrived.
 *
 * @param options never null; see {@link OptionsDeserializer} for why the wire shape varies
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OptionsResponse(
        boolean success,
        @JsonDeserialize(using = OptionsDeserializer.class) List<Option> options,
        String error,
        String errorMessage) {

    public OptionsResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }

    /** The reason the gateway gave for refusing, under whichever of the two spellings it used. */
    public Optional<String> failure() {
        return Optional.ofNullable(errorMessage != null ? errorMessage : error);
    }
}
