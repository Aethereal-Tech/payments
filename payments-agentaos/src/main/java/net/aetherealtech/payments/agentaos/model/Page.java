package net.aetherealtech.payments.agentaos.model;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import net.aetherealtech.payments.agentaos.internal.Json;

/**
 * One page of a list endpoint: the rows, the total behind them, and whether more remain.
 *
 * <p>Every list on this gateway answers in this envelope, which is why it is one type rather than six.
 * On the wire the flag is {@code has_more}; it is read here rather than anywhere else.
 *
 * @param <T>     the row type
 * @param items   this page's rows, never null
 * @param total   how many rows match in total, across every page
 * @param hasMore whether asking for the next offset would return anything
 */
public record Page<T>(List<T> items, int total, boolean hasMore) {

    public Page {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** An empty page, for a list that matched nothing. */
    public static <T> Page<T> empty() {
        return new Page<>(List.of(), 0, false);
    }

    /** One page read from a gateway response, each row mapped by {@code mapper}. */
    public static <T> Page<T> from(final Map<String, Object> body, final Function<Map<String, Object>, T> mapper) {
        final Long total = Json.integer(body, "total");
        return new Page<>(
                Json.objects(body, "items").stream().map(mapper).toList(),
                total == null ? 0 : total.intValue(),
                Json.bool(body, "has_more"));
    }
}
