package net.aetherealtech.payments;

/**
 * One thing a {@link PaymentProvider} can or cannot do, so a consumer asks rather than guesses.
 *
 * <p>Every optional operation on {@link PaymentProvider} has a capability here, and calling one a
 * provider does not declare is an
 * {@link net.aetherealtech.payments.exception.UnsupportedCapabilityException} naming the capability
 * rather than a mysterious refusal from three layers down.
 *
 * <p>The set is per PROVIDER, not per merchant account. A capability declared here says the adapter
 * implements the operation; whether a particular account is entitled to it is between the account and
 * the provider, and shows up as a refusal at call time. Bankart, for instance, declares
 * {@link #SUBSCRIPTIONS} because the schedule API exists, while a connector without recurring enabled
 * will still refuse the call.
 */
public enum ProviderCapability {

    /** A checkout the buyer completes on the provider's own page. Every adapter here has this. */
    HOSTED_CHECKOUT,

    /** A checkout that establishes a subscription rather than a single charge. */
    RECURRING_CHECKOUT,

    /** A checkout also offers a non-browser payment entry point — {@link RedirectTarget#machinePayment()}. */
    MACHINE_PAYMENT_URL,

    /** Webhooks are signed and the adapter verifies them. Its absence is a reason not to adopt. */
    WEBHOOK_SIGNATURE,

    /** {@link PaymentProvider#refund(RefundRequest)} is implemented. */
    REFUND,

    /** Subscription events are emitted and subscription operations are implemented. */
    SUBSCRIPTIONS,

    /** A cancellation can be deferred to the end of the paid period rather than taking effect at once. */
    CANCEL_AT_PERIOD_END,

    /** A subscription can move between plans without being cancelled and recreated. */
    PLAN_CHANGE,

    /** {@link PaymentProvider#reconcile(String)} is implemented — state can be polled, not only received. */
    RECONCILE,

    /** An instrument can be stored and charged again without the buyer present. */
    TOKENIZATION
}
