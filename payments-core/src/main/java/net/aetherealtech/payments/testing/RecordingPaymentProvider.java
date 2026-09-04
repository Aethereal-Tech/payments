package net.aetherealtech.payments.testing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import net.aetherealtech.payments.InboundWebhook;
import net.aetherealtech.payments.PaymentIntent;
import net.aetherealtech.payments.PaymentProvider;
import net.aetherealtech.payments.ProviderCapability;
import net.aetherealtech.payments.RedirectTarget;
import net.aetherealtech.payments.RefundReceipt;
import net.aetherealtech.payments.RefundRequest;
import net.aetherealtech.payments.SubscriptionSnapshot;
import net.aetherealtech.payments.event.PaymentEvent;
import net.aetherealtech.payments.exception.UnsupportedCapabilityException;

/**
 * A {@link PaymentProvider} that answers what a test told it to and remembers what it was asked.
 *
 * <p>Shipped in the main artifact rather than a test jar on purpose: the consumers of this SPI are the
 * ones who need it, and a test-scoped classifier is a dependency people fail to add and then reimplement
 * badly. It costs a consumer nothing at runtime — one class, no dependencies.
 *
 * <p>It exists because the alternative in a consumer's suite is a mock, and a mocked
 * {@code PaymentProvider} lets a test assert against a checkout result no real adapter could produce:
 * an intent with no amount, a {@link RedirectTarget} with a blank URL, a snapshot with a null status.
 * Every value handed to this double goes through the same compact constructors an adapter's would, so a
 * fixture that could not exist does not compile or does not construct.
 *
 * <h2>Unscripted calls fail loudly</h2>
 *
 * <p>Asking for something nothing was scripted for is an {@link IllegalStateException} naming the method
 * and what to script — never a null and never a plausible default. A test double that quietly returns
 * nothing is how a test passes for a reason nobody intended.
 *
 * <h2>Refusals are scriptable too</h2>
 *
 * <p>{@code willFail…} queues an exception in the same order the results are queued, so a suite can
 * prove its own handling of a decline, a timeout with an unknown outcome, or an unverifiable webhook
 * without a gateway anywhere near it — which is most of what a payments consumer's error handling is
 * for and the part hardest to exercise against a real provider.
 *
 * <p>Thread-safe: the queues and the logs both tolerate concurrent use, so a consumer can point a
 * parallel suite at one instance.
 */
public final class RecordingPaymentProvider implements PaymentProvider {

    /** The default {@link #id()}, distinctive enough that it is obvious in a log if one escapes a test. */
    public static final String DEFAULT_ID = "recording";

    private final String id;
    private final Set<ProviderCapability> capabilities;

    private final Deque<Object> checkoutScript = new ArrayDeque<>();
    private final Deque<Object> webhookScript = new ArrayDeque<>();
    private final Deque<Object> reconcileScript = new ArrayDeque<>();
    private final Deque<Object> cancelScript = new ArrayDeque<>();
    private final Deque<Object> refundScript = new ArrayDeque<>();

    private final List<PaymentIntent> checkouts = new CopyOnWriteArrayList<>();
    private final List<InboundWebhook> webhooks = new CopyOnWriteArrayList<>();
    private final List<String> reconciles = new CopyOnWriteArrayList<>();
    private final List<Cancellation> cancellations = new CopyOnWriteArrayList<>();
    private final List<RefundRequest> refunds = new CopyOnWriteArrayList<>();

    private RecordingPaymentProvider(final String id, final Set<ProviderCapability> capabilities) {
        this.id = id;
        this.capabilities = capabilities;
    }

    /** One recorded {@link PaymentProvider#cancelSubscription(String, boolean)} call. */
    public record Cancellation(String subscriptionRef, boolean atPeriodEnd) {
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A provider declaring every capability, scripted with nothing yet. */
    public static RecordingPaymentProvider withEveryCapability() {
        return builder().build();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        return capabilities;
    }

    @Override
    public RedirectTarget startCheckout(final PaymentIntent intent) {
        Objects.requireNonNull(intent, "intent must not be null");
        require(ProviderCapability.HOSTED_CHECKOUT);
        if (intent.isRecurring()) {
            require(ProviderCapability.RECURRING_CHECKOUT);
        }
        checkouts.add(intent);
        return next(checkoutScript, "startCheckout", "willReturnCheckout");
    }

    @Override
    public PaymentEvent handleWebhook(final InboundWebhook webhook) {
        Objects.requireNonNull(webhook, "webhook must not be null");
        webhooks.add(webhook);
        return next(webhookScript, "handleWebhook", "willReturnEvent");
    }

    @Override
    public SubscriptionSnapshot reconcile(final String subscriptionRef) {
        require(ProviderCapability.RECONCILE);
        reconciles.add(subscriptionRef);
        return next(reconcileScript, "reconcile", "willReturnSnapshot");
    }

    @Override
    public SubscriptionSnapshot cancelSubscription(final String subscriptionRef, final boolean atPeriodEnd) {
        require(ProviderCapability.SUBSCRIPTIONS);
        if (atPeriodEnd) {
            require(ProviderCapability.CANCEL_AT_PERIOD_END);
        }
        cancellations.add(new Cancellation(subscriptionRef, atPeriodEnd));
        return next(cancelScript, "cancelSubscription", "willReturnCancellation");
    }

    @Override
    public RefundReceipt refund(final RefundRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        require(ProviderCapability.REFUND);
        refunds.add(request);
        return next(refundScript, "refund", "willReturnRefund");
    }

    // ------------------------------------------------------------------ scripting

    /** Queues one answer for the next {@link #startCheckout(PaymentIntent)}. */
    public RecordingPaymentProvider willReturnCheckout(final RedirectTarget target) {
        return queue(checkoutScript, target);
    }

    /** Queues one answer for the next {@link #handleWebhook(InboundWebhook)}. */
    public RecordingPaymentProvider willReturnEvent(final PaymentEvent event) {
        return queue(webhookScript, event);
    }

    /** Queues one answer for the next {@link #reconcile(String)}. */
    public RecordingPaymentProvider willReturnSnapshot(final SubscriptionSnapshot snapshot) {
        return queue(reconcileScript, snapshot);
    }

    /** Queues one answer for the next {@link #cancelSubscription(String, boolean)}. */
    public RecordingPaymentProvider willReturnCancellation(final SubscriptionSnapshot snapshot) {
        return queue(cancelScript, snapshot);
    }

    /** Queues one answer for the next {@link #refund(RefundRequest)}. */
    public RecordingPaymentProvider willReturnRefund(final RefundReceipt receipt) {
        return queue(refundScript, receipt);
    }

    public RecordingPaymentProvider willFailCheckout(final RuntimeException failure) {
        return queue(checkoutScript, failure);
    }

    public RecordingPaymentProvider willFailWebhook(final RuntimeException failure) {
        return queue(webhookScript, failure);
    }

    public RecordingPaymentProvider willFailReconcile(final RuntimeException failure) {
        return queue(reconcileScript, failure);
    }

    public RecordingPaymentProvider willFailCancellation(final RuntimeException failure) {
        return queue(cancelScript, failure);
    }

    public RecordingPaymentProvider willFailRefund(final RuntimeException failure) {
        return queue(refundScript, failure);
    }

    // ------------------------------------------------------------------ what was asked

    /** Every {@link #startCheckout(PaymentIntent)} argument, in order. */
    public List<PaymentIntent> checkouts() {
        return List.copyOf(checkouts);
    }

    /** Every {@link #handleWebhook(InboundWebhook)} argument, in order. */
    public List<InboundWebhook> webhooks() {
        return List.copyOf(webhooks);
    }

    /** Every {@link #reconcile(String)} argument, in order. */
    public List<String> reconciles() {
        return List.copyOf(reconciles);
    }

    /** Every {@link #cancelSubscription(String, boolean)} call, in order. */
    public List<Cancellation> cancellations() {
        return List.copyOf(cancellations);
    }

    /** Every {@link #refund(RefundRequest)} argument, in order. */
    public List<RefundRequest> refunds() {
        return List.copyOf(refunds);
    }

    /** The most recent checkout intent. */
    public PaymentIntent lastCheckout() {
        return last(checkouts, "startCheckout");
    }

    /** The most recent refund request. */
    public RefundRequest lastRefund() {
        return last(refunds, "refund");
    }

    /** Forgets every recorded call, keeping whatever is still scripted. */
    public void resetCalls() {
        checkouts.clear();
        webhooks.clear();
        reconciles.clear();
        cancellations.clear();
        refunds.clear();
    }

    /** True when every queued answer has been handed out — what a test asserts at the end. */
    public boolean scriptExhausted() {
        synchronized (this) {
            return checkoutScript.isEmpty() && webhookScript.isEmpty() && reconcileScript.isEmpty()
                    && cancelScript.isEmpty() && refundScript.isEmpty();
        }
    }

    // ------------------------------------------------------------------ plumbing

    private RecordingPaymentProvider queue(final Deque<Object> script, final Object answer) {
        Objects.requireNonNull(answer, "a scripted answer must not be null");
        synchronized (this) {
            script.addLast(answer);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    private <T> T next(final Deque<Object> script, final String method, final String scriptMethod) {
        final Object answer;
        synchronized (this) {
            answer = script.pollFirst();
        }
        if (answer == null) {
            throw new IllegalStateException(
                    id + "." + method + "() was called with nothing scripted — queue one with " + scriptMethod + "(…)");
        }
        if (answer instanceof RuntimeException failure) {
            throw failure;
        }
        return (T) answer;
    }

    private void require(final ProviderCapability capability) {
        if (!capabilities.contains(capability)) {
            throw new UnsupportedCapabilityException(id, capability);
        }
    }

    private static <T> T last(final List<T> recorded, final String method) {
        if (recorded.isEmpty()) {
            throw new IllegalStateException(method + "() has not been called");
        }
        return recorded.get(recorded.size() - 1);
    }

    /** Configures the identity and the capability set; everything else is scripted on the instance. */
    public static final class Builder {
        private String id = DEFAULT_ID;
        private Set<ProviderCapability> capabilities = EnumSet.allOf(ProviderCapability.class);

        private Builder() {
        }

        public Builder id(final String id) {
            this.id = Objects.requireNonNull(id, "id must not be null");
            return this;
        }

        /** Exactly these, replacing the default of every capability. */
        public Builder capabilities(final ProviderCapability... capabilities) {
            this.capabilities = capabilities.length == 0
                    ? EnumSet.noneOf(ProviderCapability.class)
                    : EnumSet.copyOf(List.of(capabilities));
            return this;
        }

        /** Everything except these — the shape of "prove my code handles a provider that cannot refund". */
        public Builder without(final ProviderCapability... capabilities) {
            final EnumSet<ProviderCapability> remaining = EnumSet.allOf(ProviderCapability.class);
            List.of(capabilities).forEach(remaining::remove);
            this.capabilities = remaining;
            return this;
        }

        public RecordingPaymentProvider build() {
            return new RecordingPaymentProvider(id, Set.copyOf(capabilities));
        }
    }
}
