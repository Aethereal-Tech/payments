# Payment SPI

## Purpose

The provider-neutral SPI in `payments-core`: the single `PaymentProvider` entry point and its capability gate,
the value types, the typed refusal hierarchy, and the sealed `PaymentEvent` hierarchy with its `EventHeader`.

## Requirements

### Requirement: PaymentProvider is the whole entry point, gated by capability
`PaymentProvider` SHALL be the whole entry point, with each operation requiring the capability named below and
answering with the type named below:

| Operation | Needs the capability | Answers with |
|---|---|---|
| `id()` | — | a stable lower-case provider id |
| `capabilities()` | — | the immutable set of declared capabilities |
| `startCheckout(PaymentIntent)` | `HOSTED_CHECKOUT` (+ `RECURRING_CHECKOUT`) | `RedirectTarget` |
| `handleWebhook(InboundWebhook)` | `WEBHOOK_SIGNATURE` | one `PaymentEvent` |
| `reconcile(String)` | `RECONCILE` | `SubscriptionSnapshot` |
| `cancelSubscription(String, boolean)` | `SUBSCRIPTIONS` (+ `CANCEL_AT_PERIOD_END`) | `SubscriptionSnapshot` |
| `refund(RefundRequest)` | `REFUND` | `RefundReceipt` |

Calling an operation whose capability a provider does not declare SHALL raise `UnsupportedCapabilityException`
naming the capability — never a null, a no-op, or a plausible-looking result that quietly did nothing.

#### Scenario: An operation is called without its capability
- **WHEN** an operation is called on a `PaymentProvider` that does not declare the capability it needs
- **THEN** `UnsupportedCapabilityException` is raised, naming the missing capability

#### Scenario: cancelSubscription at period end needs CANCEL_AT_PERIOD_END
- **WHEN** `cancelSubscription(ref, true)` is called on a provider that declares `SUBSCRIPTIONS` but not
  `CANCEL_AT_PERIOD_END`
- **THEN** `UnsupportedCapabilityException` is raised rather than cancelling immediately

### Requirement: Ten capabilities exist, and callers ask rather than guess
The capability set SHALL be exactly: `HOSTED_CHECKOUT`, `RECURRING_CHECKOUT`, `MACHINE_PAYMENT_URL`,
`WEBHOOK_SIGNATURE`, `REFUND`, `SUBSCRIPTIONS`, `CANCEL_AT_PERIOD_END`, `PLAN_CHANGE`, `RECONCILE`,
`TOKENIZATION`.

#### Scenario: A capability not in the set cannot be declared
- **WHEN** a provider's `capabilities()` is inspected
- **THEN** every member is one of the ten named capabilities

### Requirement: Values carry no arithmetic and no provider assumptions
The core value types SHALL be `Money` (a `BigDecimal` plus an ISO 4217 code, no arithmetic), `PaymentIntent`,
`Recurrence`, `PeriodUnit`, `Plan` (no price), `Customer`, `RedirectTarget`, `RefundRequest`, `RefundReceipt`,
`SubscriptionSnapshot`, `SubscriptionStatus`, `EffectiveTiming`, `InboundWebhook`.

#### Scenario: Money exposes no addition or conversion
- **WHEN** `Money` is used by a caller
- **THEN** no arithmetic operation (addition, proration, currency conversion) is available on it

### Requirement: Refusals form a typed hierarchy under PaymentException
Every refusal SHALL extend `PaymentException` (unchecked): `PaymentDeclinedException`,
`PaymentProviderException` (carries `outcomeUnknown()`), `WebhookVerificationException` (carries `reason()`),
`UnsupportedCapabilityException`.

#### Scenario: A single catch covers every refusal
- **WHEN** any of the four named exceptions is thrown
- **THEN** catching `PaymentException` catches it

#### Scenario: A provider exception with an unknown outcome signals recovery by asking, not repeating
- **WHEN** a `PaymentProviderException` is raised with `outcomeUnknown() == true`
- **THEN** the caller is expected to recover by asking (reconcile or a status lookup), not by resending the
  request

### Requirement: PaymentEvent is sealed over fourteen events, each carrying an EventHeader
`PaymentEvent` SHALL be sealed, so a consumer's exhaustive switch stops compiling when a member is added. Every
event SHALL carry an `EventHeader`: `eventId` (dedupe), `provider`, `occurredAt` (order), `acknowledgement` (the
exact body to answer the request with), `rawPayload`.

| Event | Carries | Notes |
|---|---|---|
| `CheckoutCompleted` | checkoutRef, merchantReference, amount, paymentRef, metadata | the outcome, not the buyer's arrival at a success URL |
| `PaymentSucceeded` | paymentRef, merchantReference, amount, subscriptionRef | subscriptionRef null ⇒ one-off |
| `PaymentFailed` | + subscriptionStatus, declineCode, reason | ONE attempt; not the end of the road |
| `SubscriptionCreated` | status, plan, amount, currentPeriodEnd, trialEndsAt, customerRef | existence, not payment |
| `SubscriptionRenewed` | status, currentPeriodEnd (non-null), plan, amount, paymentRef | the new expiry is the whole point |
| `SubscriptionPlanChanged` | previousPlan, newPlan, timing, effectiveAt | **no money, deliberately** |
| `SubscriptionCancelled` | status, timing, effectiveAt, reason | read `timing` before revoking anything |
| `SubscriptionExpired` | status, expiredAt | nobody decided it |
| `DunningExhausted` | status, amount, attempts, lastPaymentRef, reason | **terminal** |
| `TrialStarted` | trialEndsAt, plan | optional in practice |
| `TrialEnding` | trialEndsAt, amount | a notice, not a state change |
| `Refunded` | refundRef, paymentRef, amount, merchantReference, reason | the money landing, unlike `RefundReceipt` |
| `ChargebackOpened` | paymentRef, chargebackRef, amount, reasonCode, reason | audit fact |
| `ChargebackReversed` | paymentRef, chargebackRef, amount, reason | audit fact |
| `UnknownEvent` | providerEventType + the raw payload | verified but unmodelled |

#### Scenario: Adding a new PaymentEvent member breaks an exhaustive switch
- **WHEN** a new member is added to the sealed `PaymentEvent` hierarchy
- **THEN** a consumer's pre-existing exhaustive switch over `PaymentEvent` stops compiling

#### Scenario: SubscriptionPlanChanged carries no money
- **WHEN** `SubscriptionPlanChanged` is emitted
- **THEN** it carries `previousPlan`, `newPlan`, `timing` and `effectiveAt`, and no amount

#### Scenario: DunningExhausted is terminal, unlike PaymentFailed
- **WHEN** `PaymentFailed` is emitted
- **THEN** it represents one attempt, not the end of the road
- **AND** only `DunningExhausted` represents the terminal state
