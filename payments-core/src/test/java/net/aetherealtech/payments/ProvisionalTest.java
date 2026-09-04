package net.aetherealtech.payments;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisionalTest {

    @Provisional("Fabricated for this test — not read from any provider's documentation.")
    private static final class AnnotatedType {

        @Provisional("Fabricated for this test — models a field read from an SDK source file.")
        private String field;

        @Provisional("Fabricated for this test — models a guessed response mapping.")
        private String annotatedMethod() {
            return null;
        }
    }

    /**
     * This is the property {@code AgentaOS}'s own inventory test depends on: a marker that only
     * showed up in Javadoc would not survive to a reflective scan at runtime.
     */
    @Test
    void theValueIsReadableReflectivelyOnAType() {
        final Provisional annotation = AnnotatedType.class.getAnnotation(Provisional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .isEqualTo("Fabricated for this test — not read from any provider's documentation.");
    }

    @Test
    void theValueIsReadableReflectivelyOnAField() throws NoSuchFieldException {
        final Field field = AnnotatedType.class.getDeclaredField("field");
        final Provisional annotation = field.getAnnotation(Provisional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .isEqualTo("Fabricated for this test — models a field read from an SDK source file.");
    }

    @Test
    void theValueIsReadableReflectivelyOnAMethod() throws NoSuchMethodException {
        final Method method = AnnotatedType.class.getDeclaredMethod("annotatedMethod");
        final Provisional annotation = method.getAnnotation(Provisional.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .isEqualTo("Fabricated for this test — models a guessed response mapping.");
    }
}
