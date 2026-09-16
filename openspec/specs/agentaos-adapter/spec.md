# AgentaOS adapter

## Purpose

`payments-agentaos` is a client and `PaymentProvider` for AgentaOS, an Estonian merchant-of-record billing
platform, Stripe-backed on the card rail (`stripeSubscriptionId` and `stripeCustomerId` appear on its own
objects) with an on-chain x402 rail alongside. There is no API reference for AgentaOS anywhere; the open-source
TypeScript SDK `@agentaos/pay` (2.1.0, `github.com/AgentaOS/agentaos`, `packages/pay/src`) is the entire
specification, and it states what a client SENDS rather than what the server ACCEPTS. This capability records
the wire facts, the client surface, the capabilities declared and refused, the status and event mappings, the
synthesised event id, and the provisional inventory.

## Requirements

### Requirement: Base URL, auth and the camelCase/snake_case asymmetry
**Base URL** SHALL be `https://api.agentaos.ai`, with the version segment in the path: every route begins
`/api/v1/gateway/`. **Auth** SHALL be `x-api-key: <key>` for keys prefixed `sk_live_` / `sk_test_`, or a
three-part dotted JWT as `authorization: Bearer <token>`.

**Request bodies are camelCase and untransformed; responses arrive snake_case.** The SDK converts responses
client-side and explicitly does not transform requests. This asymmetry is real and is the easiest thing here to
get wrong.

#### Scenario: A request body is sent camelCase, untransformed
- **WHEN** a request is sent to AgentaOS
- **THEN** its body keys are camelCase and are not transformed before sending

#### Scenario: A response body arrives snake_case and is converted client-side
- **WHEN** a response is received from AgentaOS
- **THEN** its body keys arrive snake_case and are converted client-side before use

### Requirement: Idempotency and pagination
An **`idempotency-key`** header SHALL be sent on every POST, never on GET or DELETE. Pagination SHALL take
`limit` + `offset` in and answer `{items, total, has_more}` out. `limit` outside 1–100 and a negative offset
SHALL be refused before the request is sent.

#### Scenario: idempotency-key is absent from GET and DELETE
- **WHEN** a GET or DELETE request is sent
- **THEN** no `idempotency-key` header is present

#### Scenario: An out-of-range limit is refused before the request
- **WHEN** `limit` is outside 1–100, or `offset` is negative
- **THEN** the client refuses before sending the request

### Requirement: Amounts are inconsistent by resource, on purpose or otherwise
Checkouts, payment links, transactions and invoices SHALL carry `amount` as a **decimal number in major units**.
Subscriptions and plan-change fields SHALL carry **integer minor units** (`unitAmountMinor`, `amountMinor`).
Webhook `amount` on checkout and send events SHALL be a **string**. Currencies SHALL be **EUR and USD** only on
a client call refusing anything else; on a webhook an unsupported currency's amount SHALL be **omitted** rather
than scaled by a guessed exponent, with the event still emitted.

Only EUR and USD have a minor-unit exponent stated anywhere in the SDK.

#### Scenario: A checkout amount is a decimal major-unit number
- **WHEN** a checkout's `amount` is read
- **THEN** it is a decimal number in major units

#### Scenario: A subscription amount is an integer minor-unit field
- **WHEN** a subscription's `unitAmountMinor` or `amountMinor` is read
- **THEN** it is an integer in minor units

#### Scenario: An unknown currency on a client call is refused
- **WHEN** a client call is made with a currency other than EUR or USD
- **THEN** it is refused as `unsupported_currency`

#### Scenario: An unknown currency on a webhook omits the amount rather than guessing
- **WHEN** a webhook payload carries an amount in a currency other than EUR or USD
- **THEN** the amount is omitted from the emitted event, and the event is still emitted

### Requirement: Webhook signature is HMAC-SHA256 over timestamp-dot-body
The `x-agentaos-signature` header SHALL carry `t=<unix seconds>,v1=<hex>`, verified as HMAC-SHA256, hex-encoded,
over the string `<timestamp>.<raw body>`, compared with `MessageDigest.isEqual`. Default tolerance SHALL be 300
seconds, rejecting a FUTURE timestamp as well as a stale one. A 2xx SHALL acknowledge; the body is not read. A
malformed header and an unparseable `t` SHALL be distinct refusal reasons.

**Both camelCase and snake_case bodies are read** — `Json.withCamelAliases` adds camel keys with `putIfAbsent`
over `data` and `pendingPlan` only, so a merchant's own metadata keys are never rewritten.

#### Scenario: A future timestamp is rejected like a stale one
- **WHEN** the signature timestamp is more than 300 seconds in the future
- **THEN** verification is rejected, the same as a timestamp more than 300 seconds stale

#### Scenario: A malformed header and an unparseable timestamp are distinct reasons
- **WHEN** the `x-agentaos-signature` header is malformed, versus when `t` cannot be parsed
- **THEN** the two failures are reported as distinct refusal reasons

#### Scenario: Merchant metadata keys are never rewritten by camel-aliasing
- **WHEN** a webhook body carries merchant-supplied metadata keys alongside `data` and `pendingPlan`
- **THEN** `Json.withCamelAliases` adds camel keys only over `data` and `pendingPlan`, leaving merchant metadata
  keys untouched

### Requirement: Refunds do not exist in the SDK
No refund, reverse or reversal method SHALL exist anywhere in `packages/pay/src`; the changelog says cancellation
issues none. The adapter SHALL NOT declare `REFUND` and SHALL refuse rather than inventing an endpoint.

#### Scenario: refund is refused rather than calling an invented endpoint
- **WHEN** `refund` is called on `AgentaOsPaymentProvider`
- **THEN** `UnsupportedCapabilityException` is raised, since `REFUND` is not declared and no such endpoint exists

### Requirement: The event catalogue has eight members; only three are in the README
The event catalogue, from `types.ts`, SHALL be: `checkout.session.completed`, `send.completed`, `send.failed`,
`subscription.created`, `subscription.renewed`, `subscription.payment_failed`, `subscription.updated`,
`subscription.canceled`. The README documents only the first three; the five `subscription.*` events exist in
the types file alone.

#### Scenario: A subscription.* event is handled though the README does not document it
- **WHEN** a `subscription.renewed` webhook arrives
- **THEN** it is handled, even though the README's webhook table does not list it

### Requirement: Sandbox mode is unknown
There SHALL be no separate sandbox host — `sk_test_` and `sk_live_` keys both hit `https://api.agentaos.ai`, and
test mode SHALL be signalled only by the key prefix and a `livemode` boolean on webhook payloads. Whether a test
key reaches a distinct backend or merely flags the same one is not stated anywhere found.

#### Scenario: A test-prefixed key still calls the production host
- **WHEN** a client is configured with an `sk_test_` key
- **THEN** it still calls `https://api.agentaos.ai`, the same host as an `sk_live_` key

### Requirement: The client surface is one method per documented SDK operation
`AgentaOsClient`'s methods SHALL each map to exactly one documented operation:

| Method | Call |
|---|---|
| `createCheckout` | `POST /api/v1/gateway/sessions` |
| `retrieveCheckout` | `GET /api/v1/gateway/sessions/{id}` |
| `cancelCheckout` | `POST /api/v1/gateway/sessions/{id}/cancel` |
| `createPaymentLink` | `POST /api/v1/gateway/payment-links` |
| `retrievePaymentLink` | `GET /api/v1/gateway/payment-links/{id}` |
| `cancelPaymentLink` | **`DELETE`** `/api/v1/gateway/payment-links/{id}` |
| `listSubscriptions` | `GET /api/v1/gateway/subscriptions?limit=&offset=` |
| `cancelSubscription` | `POST /api/v1/gateway/subscriptions/{id}/cancel` |
| `listCustomers` | `GET /api/v1/gateway/customers?limit=&offset=` |

The two cancels disagree with each other — a checkout cancels with a POST and a payment link with a DELETE.
That is the SDK's own shape, not a tidying opportunity.

Every import in the module SHALL be `java.*`, `javax.crypto.*` or `net.aetherealtech.*` — the JDK and the SPI,
and nothing else, checkable by grep.

#### Scenario: cancelPaymentLink uses DELETE while cancelCheckout uses POST
- **WHEN** a payment link is cancelled versus when a checkout is cancelled
- **THEN** the payment link cancel is a `DELETE` and the checkout cancel is a `POST`, matching the SDK's own
  disagreement rather than being normalised

#### Scenario: No non-JDK, non-SPI import exists in the module
- **WHEN** the module's imports are grepped
- **THEN** every import is `java.*`, `javax.crypto.*` or `net.aetherealtech.*`

### Requirement: AgentaOsPaymentProvider declares seven capabilities and asserts two absences
`AgentaOsPaymentProvider` SHALL declare `HOSTED_CHECKOUT`, `RECURRING_CHECKOUT`, `MACHINE_PAYMENT_URL`,
`WEBHOOK_SIGNATURE`, `SUBSCRIPTIONS`, `CANCEL_AT_PERIOD_END`, `RECONCILE`, `PLAN_CHANGE`.

It SHALL NOT declare `REFUND`. It SHALL NOT declare `TOKENIZATION`: no instrument is stored or reused through
this API.

#### Scenario: TOKENIZATION is not declared
- **WHEN** `AgentaOsPaymentProvider.capabilities()` is inspected
- **THEN** `TOKENIZATION` is absent, since no instrument is stored or reused through this API

### Requirement: Subscription status mapping and dunning-is-over
`SubscriptionStatusMapping.toSpi` SHALL map, on the lower-cased, trimmed value, with `null` read as `UNKNOWN`
before the switch:

| AgentaOS | SPI |
|---|---|
| `incomplete` | `INCOMPLETE` |
| `incomplete_expired` | `EXPIRED` |
| `trialing` | `TRIALING` |
| `active` | `ACTIVE` |
| `past_due` | `PAST_DUE` |
| `canceled` | `CANCELLED` |
| `unpaid` | `EXPIRED` |
| `paused` | `PAUSED` |
| anything else | `UNKNOWN` |

`unpaid` SHALL map to `EXPIRED` because it is the end of dunning rather than a decision anyone took.
`dunningIsOver(status)` SHALL be defined over the SPI value, not the wire one — true for anything mapping to
`EXPIRED` or `CANCELLED`, which is `unpaid`, `canceled` **and** `incomplete_expired`.

`reconcile` SHALL page `listSubscriptions` because the SDK exposes no single-subscription read, giving up at
**100 pages** with `reconcile_exhausted` rather than following `has_more` forever.

#### Scenario: unpaid maps to EXPIRED, not CANCELLED
- **WHEN** a subscription's wire status is `unpaid`
- **THEN** the SPI status is `EXPIRED`

#### Scenario: dunningIsOver is true for incomplete_expired
- **WHEN** `dunningIsOver` is evaluated over a subscription whose wire status was `incomplete_expired`
- **THEN** it answers true, since `incomplete_expired` maps to `EXPIRED`

#### Scenario: reconcile gives up after 100 pages
- **WHEN** `reconcile` pages `listSubscriptions` and `has_more` is still true after 100 pages
- **THEN** it stops and raises `reconcile_exhausted` rather than paging forever

### Requirement: A synthesised event id, since AgentaOS sends none
`AgentaOsEventId` SHALL synthesise an event id as `"agentaos_"` + the first **32** lower-case hex characters of
`SHA-256(type + "\n" + resourceId + "\n" + signatureTimestamp)`, UTF-8, with a null type or resourceId read as
`""`.

`resourceId` SHALL be `data.sessionId` for `checkout.session.completed`, `data.transactionId` for `send.*`,
`data.id` for every `subscription.*`, and null for anything unmodelled or unparseable.

`occurredAt` SHALL be **the signature timestamp** — the only time the payload carries. `acknowledgement` SHALL
be the empty string: AgentaOS wants a 2xx and does not read the body.

**The dedupe assumption is that a redelivery re-sends the timestamp it first signed with.** If AgentaOS re-signs
a retry with a fresh `t=`, the same event yields a different id and a consumer's idempotency key stops working.

#### Scenario: The same payload and timestamp yield the same event id
- **WHEN** the same event type, resourceId and signature timestamp are hashed twice
- **THEN** the synthesised event id is identical both times

#### Scenario: occurredAt is the signature timestamp
- **WHEN** `occurredAt` is read from a synthesised event id's event
- **THEN** it equals the signature timestamp, since nothing else in the payload carries a time

### Requirement: Event mapping from payload to PaymentEvent
Event mapping SHALL be:

| Payload | Event | Condition |
|---|---|---|
| `checkout.session.completed` | `CheckoutCompleted` | needs `data.sessionId`; merchant reference read out of `metadata["merchantReference"]` |
| `subscription.created` | `SubscriptionCreated` | needs `data.id`; `trialEndsAt` is never set — nothing carries it |
| `subscription.renewed` | `SubscriptionRenewed` | **`UnknownEvent` when `currentPeriodEnd` is absent** — the new expiry is the whole point of the event, and a guessed one is worse than none |
| `subscription.payment_failed` | `PaymentFailed` with the mapped status | one attempt |
| `subscription.payment_failed` | `DunningExhausted` | when the payload's own status is terminal |
| `subscription.updated` | `SubscriptionPlanChanged`, `AT_PERIOD_END` | **only** with a `pendingPlan` and both link ids present; otherwise `UnknownEvent` |
| `subscription.canceled` | `SubscriptionCancelled` | `AT_PERIOD_END` or `IMMEDIATE` from `cancelAtPeriodEnd` |
| `send.completed`, `send.failed` | `UnknownEvent` | the outbound wallet rail — money leaving, not a customer paying |
| a verified body that will not parse | `UnknownEvent` | it authenticated, so it is a fact worth keeping |

`AT_PERIOD_END` on a plan change SHALL be hard-coded because a `pendingPlan` is by definition pending; a change
that had already taken effect would not be pending.

**No retry loop.** One `HttpClient.send`, no backoff. A timeout, a dropped connection or an interruption SHALL
be a `PaymentProviderException` with `outcomeUnknown() == true`.

#### Scenario: subscription.renewed without currentPeriodEnd becomes UnknownEvent
- **WHEN** a `subscription.renewed` webhook arrives with no `currentPeriodEnd`
- **THEN** it is read as `UnknownEvent`, rather than a `SubscriptionRenewed` with a guessed expiry

#### Scenario: subscription.updated without a pendingPlan becomes UnknownEvent
- **WHEN** a `subscription.updated` webhook arrives with no `pendingPlan`, or with one of the two link ids
  missing
- **THEN** it is read as `UnknownEvent`, not `SubscriptionPlanChanged`

#### Scenario: A terminal payment_failed status becomes DunningExhausted
- **WHEN** a `subscription.payment_failed` webhook's own status is terminal
- **THEN** it is read as `DunningExhausted`, not `PaymentFailed`

#### Scenario: A timeout is a PaymentProviderException with an unknown outcome
- **WHEN** an HTTP call times out, drops its connection, or is interrupted
- **THEN** `PaymentProviderException` is raised with `outcomeUnknown() == true`, and no retry is attempted
  internally

### Requirement: Thirteen elements are annotated Provisional, each with named evidence
`@Provisional` SHALL be applied to exactly thirteen elements, each carrying a javadoc sentence naming the
evidence it rests on. `ProvisionalInventoryTest` SHALL walk the module's compiled classes, assert every
`@Provisional` states a non-blank evidence, and pin the count at 13.

| Element | What it rests on |
|---|---|
| **TYPE** `AgentaOsEventId` | AgentaOS webhook payloads carry no event id; this identifier is this library's own construction, and its stability across a redelivery assumes AgentaOS re-sends the signature timestamp it first signed with, which nothing in `packages/pay/src` states either way. |
| **FIELD** `AgentaOsPaymentProvider.MERCHANT_REFERENCE_KEY` | `CreateCheckoutParams` and `CheckoutCompletedData` each declare a metadata map in `packages/pay/src/types.ts`, but nothing in `packages/pay/src` shows the one arriving in the other; a merchant reference riding in metadata rests on that assumed round trip. |
| **METHOD** `AgentaOsPaymentProvider.reconcile` | `packages/pay/src/resources/subscriptions.ts` declares `list`, `cancel`, `invoices`, `previewPlanChange` and `changePlan` and no `retrieve`, so this pages the list; whether the server exposes `GET /api/v1/gateway/subscriptions/{id}` is unknown. |
| **METHOD** `SubscriptionStatusMapping.dunningIsOver` | `types.ts` calls the field "Stripe subscription status, mirrored verbatim" but states nothing about which statuses mean retries have stopped; `unpaid` and `canceled` are read as terminal from Stripe's own dunning model, not from anything in `packages/pay/src`. |
| **FIELD** `AgentaOsWebhookVerifier.SIGNATURE_HEADER` | `packages/pay/src` never reads this header — `verify()` takes the signature as a string parameter — so the name comes from `examples/pay-express-shop/server.ts` (`req.headers['x-agentaos-signature']`) and the same line in `packages/pay/README.md`. |
| **FIELD** `AgentaOsConfig.DEFAULT_PAGE_SIZE` | The SDK's `types.ts` documents `ListParams.limit` as "1-100, default 10" while `packages/pay/README.md` documents default 20 for the same calls and the wallet CLI defaults to 10 — three statements, no way to tell which the server applies, so this never relies on it. |
| **METHOD** `Amounts.fromMinorUnits` | `types.ts` documents `unitAmountMinor` as "Per-cycle amount in integer minor units (e.g. 1999 = EUR 19.99)" and names only EUR and USD as currencies; the exponent for any other currency is stated nowhere in `packages/pay/src`. |
| **METHOD** `Checkout.machinePaymentUrl` | `types.ts` declares `x402Url` on `Checkout`, but a source comment in `packages/wallet/src/mcp/tools/pay-create-checkout.ts` says it "exists at runtime but may not be in the published types yet" and casts around it — the two disagree about which deployments actually return it. |
| **FIELD** `AgentaOsEventType.SUBSCRIPTION_CREATED`, `.SUBSCRIPTION_RENEWED`, `.SUBSCRIPTION_PAYMENT_FAILED`, `.SUBSCRIPTION_UPDATED`, `.SUBSCRIPTION_CANCELED` (five entries, the same sentence) | Declared in `packages/pay/src/types.ts`'s `WebhookEvent` union; `packages/pay/README.md`'s webhook table documents only `checkout.session.completed`, `send.completed` and `send.failed`. |

#### Scenario: An @Provisional element with blank evidence fails the build
- **WHEN** `ProvisionalInventoryTest` runs against an `@Provisional` element carrying blank evidence
- **THEN** the build fails

#### Scenario: A fourteenth @Provisional element fails the pinned count
- **WHEN** a new element is annotated `@Provisional` without updating `ProvisionalInventoryTest`
- **THEN** the test fails, since the count is pinned at 13

### Requirement: Commercial facts recorded for a consumer weighing adoption
Pro pricing SHALL be recorded as €49/mo + 4.0% + €0.40 per card sale; pay-as-you-grow as 4.5% + €0.50;
chargebacks as a flat €35 per dispute regardless of outcome; new accounts held at a 10–15% reserve released
after 90 days clean with disputes under 0.5%; governing law Estonian, Harju County Court.

#### Scenario: A chargeback fee is recorded independent of dispute outcome
- **WHEN** a chargeback occurs
- **THEN** the recorded fee is a flat €35 per dispute, regardless of the outcome
