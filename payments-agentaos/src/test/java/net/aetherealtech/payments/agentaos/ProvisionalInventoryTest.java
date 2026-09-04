package net.aetherealtech.payments.agentaos;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import net.aetherealtech.payments.Provisional;

/**
 * The inventory of everything in this module that was modelled from indirect evidence.
 *
 * <p>The count is written down here on purpose. AgentaOS publishes no API reference, so a guess is a
 * normal thing to have to make and a normal thing to forget you made — and a marker that can be added
 * without anybody noticing is decoration rather than a record. Adding or removing one fails this test
 * and forces the number, and the reason, to be looked at.
 */
class ProvisionalInventoryTest {

    /** Every {@link Provisional} in this module. Change this only alongside the inventory it counts. */
    private static final int EXPECTED = 13;

    @Test
    void everyProvisionalElementStatesItsEvidenceAndTheInventoryIsTheSizeItSaysItIs() {
        final List<String> inventory = inventory();

        System.out.println("@Provisional inventory (" + inventory.size() + "):");
        inventory.forEach(entry -> System.out.println("  " + entry));

        assertThat(inventory)
                .as("adding a @Provisional without agreeing the number is what this test exists to catch")
                .hasSize(EXPECTED);
    }

    @Test
    void theInventoryCoversTheThingsThisAdapterHadToGuessAt() {
        final List<String> inventory = inventory();

        assertThat(inventory)
                .anyMatch(entry -> entry.startsWith("TYPE AgentaOsEventId"))
                .anyMatch(entry -> entry.startsWith("FIELD AgentaOsWebhookVerifier.SIGNATURE_HEADER"))
                .anyMatch(entry -> entry.startsWith("FIELD AgentaOsConfig.DEFAULT_PAGE_SIZE"))
                .anyMatch(entry -> entry.startsWith("FIELD AgentaOsPaymentProvider.MERCHANT_REFERENCE_KEY"))
                .anyMatch(entry -> entry.startsWith("METHOD AgentaOsPaymentProvider.reconcile"))
                .anyMatch(entry -> entry.startsWith("METHOD SubscriptionStatusMapping.dunningIsOver"))
                .anyMatch(entry -> entry.startsWith("METHOD Amounts.fromMinorUnits"))
                .anyMatch(entry -> entry.startsWith("METHOD Checkout.machinePaymentUrl"));
        // The five subscription webhooks the README does not document.
        assertThat(inventory.stream()
                .filter(entry -> entry.startsWith("FIELD AgentaOsEventType.SUBSCRIPTION_"))
                .count())
                .isEqualTo(5);
    }

    private static List<String> inventory() {
        final List<String> entries = new ArrayList<>();
        for (final Class<?> type : moduleClasses()) {
            collect(entries, "TYPE", type.getSimpleName(), type);
            for (final Field field : type.getDeclaredFields()) {
                collect(entries, "FIELD", type.getSimpleName() + "." + field.getName(), field);
            }
            for (final Method method : type.getDeclaredMethods()) {
                collectExecutable(entries, type, method.getName(), method);
            }
            for (final Executable constructor : type.getDeclaredConstructors()) {
                collectExecutable(entries, type, "<init>", constructor);
            }
            if (type.isRecord()) {
                for (final RecordComponent component : type.getRecordComponents()) {
                    collect(entries, "RECORD COMPONENT",
                            type.getSimpleName() + "." + component.getName(), component);
                }
            }
        }
        return entries.stream().distinct().sorted().toList();
    }

    private static void collectExecutable(
            final List<String> entries, final Class<?> type, final String name, final Executable executable) {
        collect(entries, executable instanceof Method ? "METHOD" : "CONSTRUCTOR",
                type.getSimpleName() + "." + name, executable);
        for (final Parameter parameter : executable.getParameters()) {
            collect(entries, "PARAMETER",
                    type.getSimpleName() + "." + name + "(" + parameter.getName() + ")", parameter);
        }
    }

    private static void collect(
            final List<String> entries, final String kind, final String name, final AnnotatedElement element) {
        final Provisional provisional = annotation(element);
        if (provisional == null) {
            return;
        }
        assertThat(provisional.value())
                .as("%s %s must state what it rests on", kind, name)
                .isNotBlank();
        entries.add(kind + " " + name + " — " + provisional.value());
    }

    private static Provisional annotation(final AnnotatedElement element) {
        for (final Annotation annotation : element.getDeclaredAnnotations()) {
            if (annotation instanceof Provisional provisional) {
                return provisional;
            }
        }
        return null;
    }

    private static List<Class<?>> moduleClasses() {
        final Path root = classesRoot();
        try (Stream<Path> files = Files.walk(root)) {
            return files
                    .filter(path -> path.toString().endsWith(".class"))
                    .map(path -> root.relativize(path).toString()
                            .replace(File.separatorChar, '.')
                            .replace(".class", ""))
                    .sorted()
                    .map(ProvisionalInventoryTest::load)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("could not walk " + root, e);
        }
    }

    private static Path classesRoot() {
        try {
            return Path.of(AgentaOsPaymentProvider.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("could not locate this module's compiled classes", e);
        }
    }

    private static Class<?> load(final String name) {
        try {
            return Class.forName(name, false, ProvisionalInventoryTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("could not load " + name, e);
        }
    }
}
