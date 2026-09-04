package net.aetherealtech.payments;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This element was modelled from indirect evidence, and no live provider has confirmed it.
 *
 * <p>It exists because one adapter in this repository has no specification to be written against.
 * AgentaOS publishes no API reference; its open-source TypeScript SDK is the only statement of the wire
 * format, and reading a client's source tells you what that client sends, not what the server accepts.
 * The two are usually the same and occasionally are not.
 *
 * <p>The distinction is worth a marker rather than a paragraph in a README because it changes what a
 * failure MEANS. A signature mismatch in a verified adapter is a bug in this library or an attack; the
 * same mismatch in a provisional one may simply be a guess that was wrong, and the first thing to check
 * is the assumption rather than the code. {@link #value()} says what the guess rests on, so that check
 * starts from evidence instead of from scratch.
 *
 * <p>Retained at runtime so a test can enumerate the inventory and assert every provisional element
 * states its evidence — a marker that drifted into decoration would be worse than none.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
        ElementType.TYPE,
        ElementType.METHOD,
        ElementType.CONSTRUCTOR,
        ElementType.FIELD,
        ElementType.RECORD_COMPONENT,
        ElementType.PARAMETER
})
public @interface Provisional {

    /**
     * What this is based on, in one sentence naming the evidence — the SDK file and symbol it was read
     * from, or the fact that nothing stated it and this is the defensive reading.
     */
    String value();
}
