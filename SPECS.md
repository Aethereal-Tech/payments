# PRESENT

What exists. Rules live in `CLAUDE.md`; this file is the record.

## Artifacts and dependency posture

| Artifact | What it is | Runtime dependencies |
|---|---|---|
| `net.aetherealtech:payments-core` | The provider-neutral SPI, the model, the events, the refusals, `RecordingPaymentProvider`, `@Provisional` | **none** |
| `net.aetherealtech:payments-bankart` | The Bankart (IXOPAY) Transaction API v3 client and its `PaymentProvider` | `com.fasterxml.jackson.core:jackson-databind` |
| `net.aetherealtech:payments-agentaos` | The AgentaOS gateway client and its `PaymentProvider` | **none** — internal JSON reader/writer |

All three publish from one commit at one version. JDK 25; `maven.compiler.release=25`, so the bytecode
version and the API surface cannot disagree.

The posture is checkable — `dependency:tree` shows `payments-core` with nothing under it,
`payments-agentaos` with `payments-core` and nothing else, and `payments-bankart` with `payments-core` plus
`jackson-databind` and its own two transitives. The root pom imports the **`jackson-bom`** rather than
pinning `jackson-databind` alone: pinning the one artifact while WireMock brought its own `jackson-core` and
`jackson-annotations` produced databind 2.22.2 against annotations 2.20, and the first stub failed with
`NoClassDefFoundError: com/fasterxml/jackson/annotation/JsonSerializeAs` — a class that exists in 2.22 and
not in 2.20. Nearest-wins mediation cannot see that the three are one library. It is `dependencyManagement`
only, so it constrains versions without putting anything on a classpath, and the two zero-dependency modules
stay at zero.

`v0.1.0` was `net.aetherealtech:bankart-gateway` — one artifact, one gateway. It stays published under that
name as the last single-module release. The reactor's first release is `v0.2.0`.

## The SPI — `payments-core`

`PaymentProvider` is the whole entry point:

| Operation | Needs the capability | Answers with |
|---|---|---|
| `id()` | — | a stable lower-case provider id |
| `capabilities()` | — | the immutable set below |
| `startCheckout(PaymentIntent)` | `HOSTED_CHECKOUT` (+ `RECURRING_CHECKOUT`) | `RedirectTarget` |
| `handleWebhook(InboundWebhook)` | `WEBHOOK_SIGNATURE` | one `PaymentEvent` |
| `reconcile(String)` | `RECONCILE` | `SubscriptionSnapshot` |
| `cancelSubscription(String, boolean)` | `SUBSCRIPTIONS` (+ `CANCEL_AT_PERIOD_END`) | `SubscriptionSnapshot` |
| `refund(RefundRequest)` | `REFUND` | `RefundReceipt` |

Capabilities: `HOSTED_CHECKOUT`, `RECURRING_CHECKOUT`, `MACHINE_PAYMENT_URL`, `WEBHOOK_SIGNATURE`,
`REFUND`, `SUBSCRIPTIONS`, `CANCEL_AT_PERIOD_END`, `PLAN_CHANGE`, `RECONCILE`, `TOKENIZATION`.

Values: `Money` (BigDecimal + ISO 4217, no arithmetic), `PaymentIntent`, `Recurrence`, `PeriodUnit`,
`Plan` (no price), `Customer`, `RedirectTarget`, `RefundRequest`, `RefundReceipt`, `SubscriptionSnapshot`,
`SubscriptionStatus`, `EffectiveTiming`, `InboundWebhook`.

Refusals: `PaymentException` (base, unchecked) → `PaymentDeclinedException`, `PaymentProviderException`
(carries `outcomeUnknown()`), `WebhookVerificationException` (carries `reason()`),
`UnsupportedCapabilityException`.

### Events

`PaymentEvent` is sealed, so a consumer's exhaustive switch stops compiling when one is added.

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

Every event carries an `EventHeader`: `eventId` (dedupe), `provider`, `occurredAt` (order),
`acknowledgement` (the exact body to answer the request with), `rawPayload`.

## Bankart — `payments-bankart`

White-label IXOPAY. Bankart's `gateway.bankart.si/documentation/apiv3` and IXOPAY's
`documentation.ixopay.com` describe the same API.

**Endpoints.** `POST /transaction/{apiKey}/{debit|preauthorize|capture|void|refund|register|deregister|payout|incrementalAuthorization}`;
`POST /schedule/{apiKey}/start`, `POST /schedule/{apiKey}/{scheduleId}/{update|pause|continue|cancel}`,
`GET /schedule/{apiKey}/{scheduleId}/get`; `GET /status/{apiKey}/getByUuid/{uuid}` and
`GET /status/{apiKey}/getByMerchantTransactionId/{id}`; `POST /options/{apiKey}/{optionsName}`.
Base URL `https://gateway.bankart.si/api/v3`.

**Auth.** HTTP Basic (`Authorization: Basic base64(user:password)`), always. The API key is a PATH segment,
never a header.

**Signature.** `X-Signature`, a **bare Base64** value — not `Gateway KEY:SIG`. HMAC-SHA512, keyed with the
connector's shared secret, over five components joined by a single `\n`:

```
METHOD
sha512hex(request body, exactly as transmitted)
Content-Type header value
Date header value (X-Date takes precedence)
request URI
```

The inner body hash is **hex**; the outer HMAC is **binary → Base64, not hex**. That asymmetry is the most
commonly-gotten-wrong step and both vendors' docs warn about it in as many words. Signing is a per-connector
setting ("API: Enable Request Signing"), so a connector may legitimately send unsigned callbacks; the
verifier here never accepts one.

**Where the discrepancy was settled:** the OpenAPI specification does not mention signatures at all — its
only `securityScheme` is `basicAuth`. The bare-Base64 form comes from the prose documentation's own worked
example, which reproduces. Pinned by a test naming that source.

**Amounts.** Decimal strings (`"15.75"`), precision per ISO 4217. **MKD is supported, 2 decimals.**

**Notifications.** One `Callback` shape for everything, POSTed to the transaction's `callbackUrl`.
`result` ∈ {OK, PENDING, ERROR}. **Answer HTTP 200 with the literal body `OK`** — anything else and the
gateway retries immediately, then at 1, 5, 15, 60, 120, 180 and 720 minutes, then every 24 hours for 7 days.
`transactionType` ∈ {DEBIT, CAPTURE, DEREGISTER, PREAUTHORIZE, REFUND, REGISTER, VOID, CHARGEBACK,
CHARGEBACK-REVERSAL, PAYOUT, INCREMENTAL-AUTHORIZATION, DISPUTE, DISPUTE-REVERSAL}.

**There is no `SCHEDULE` notification type**, and this is the single most consequential fact about Bankart
as a subscription provider: a recurring charge arrives as an ordinary transaction callback with a
`scheduleData` field populated, and there is no discriminator distinguishing "schedule paused" from
"schedule cancelled" from "renewal failed". Subscription state is inferred from
`scheduleData.scheduleStatus` alongside the transaction's own `result`, or polled with `reconcile`.

**`returnData`** is a `oneOf` discriminated on `_TYPE` ∈ {`cardData`, `phoneData`, `ibanData`,
`walletData`}. There is no separate SEPA variant; IBAN covers it.

**Status API rate limit: 5 requests per minute per `transactionUuid` or `merchantTransactionId`**, HTTP 429
beyond it. Stated in the prose docs only; the OpenAPI spec states no limit anywhere. A poller must respect
it.

**Sandbox.** A separate host, `https://bankart.paymentsandbox.cloud/api/v3`, with its own OpenAPI document
at `/Schema/V3/OpenApiSpecification.yml`. **Credentials are not self-service** — they are issued by hand by
the gateway administrators / an integration engineer. This is why nothing here is verified against a live
sandbox (FUTURE 1).

### What `BankartClient` does

`TransactionResponse` from every transaction operation, `ScheduleResponse` from every schedule one.

| Method | Call |
|---|---|
| `debit`, `preauthorize`, `capture`, `voidTransaction`, `refund`, `register`, `deregister`, `payout`, `incrementalAuthorization` | `POST /transaction/{apiKey}/{operation}` |
| `startSchedule` | `POST /schedule/{apiKey}/start` |
| `updateSchedule`, `pauseSchedule`, `continueSchedule`, `cancelSchedule` | `POST /schedule/{apiKey}/{scheduleId}/{update\|pause\|continue\|cancel}` |
| `showSchedule` | `GET /schedule/{apiKey}/{scheduleId}/get` |
| `options` | `POST /options/{apiKey}/{optionsName}` |
| `statusByUuid`, `statusByMerchantTransactionId` | `GET /status/{apiKey}/getBy…` |
| `startCheckout`, `startRegistration` | `debit` / `register` narrowed to a `RedirectResult` |

`debit` refuses a `captureInMinutes` by name rather than sending it: the field applies to `preauthorize`, and
a gateway that ignores it silently would leave a caller believing in a delayed capture that never happens.

Schedule and options types: `Schedule`, `ScheduleData`, `ScheduleStatus`, `SchedulePeriodUnit`,
`StartScheduleRequest`, `UpdateScheduleRequest`, `ContinueScheduleRequest`, `ScheduleResponse`,
`IncrementalAuthorizationRequest`, `OptionsRequest`, `OptionsResponse`, `Option`, `PayByLink`,
`PayByLinkData`.

**`returnData` is a sealed `ReturnData`** permitting `CardData`, `ReturnPhoneData`, `ReturnIbanData` and
`ReturnWalletData`, deserialized by `ReturnDataDeserializer` on the `_TYPE` discriminator. It is the one
source change a `bankart-gateway:0.1.0` caller must make: `returnData()` was `CardData` and is now
`ReturnData`, and `cardData()` — `Optional<CardData>`, on `Notification`, `TransactionResponse` and
`StatusResponse` alike — is the old reading kept, with the "or it was not a card" case made explicit. On
`0.x` that is a `feat:`; the README's "Upgrading" section is where a consumer meets it.

### What `BankartPaymentProvider` declares

`HOSTED_CHECKOUT`, `RECURRING_CHECKOUT`, `WEBHOOK_SIGNATURE`, `REFUND`, `SUBSCRIPTIONS`, `PLAN_CHANGE`,
`RECONCILE`, `TOKENIZATION`.

**Not `CANCEL_AT_PERIOD_END`**: `cancelSchedule` has no deferral — there is no "cancel at the end of the
paid period" anywhere in the schedule API, so the capability would be a promise the gateway cannot keep.
**Not `MACHINE_PAYMENT_URL`**: no non-browser payment rail exists here. Both absences are asserted, not
merely omitted.

`reconcile(scheduleId)` reads `GET /schedule/{apiKey}/{scheduleId}/get` — the direct answer to a webhook
schema that cannot express subscription state.

**`ScheduleStatus` → `SubscriptionStatus`** (`BankartPaymentProvider.subscriptionStatus`, matched
case-insensitively; `null` and anything unrecognised land on `UNKNOWN`):

| Wire | Enum | SPI |
|---|---|---|
| `ACTIVE` | `ACTIVE` | `ACTIVE` |
| `PAUSED` | `PAUSED` | `PAUSED` |
| `CANCELLED` | `CANCELLED` | `CANCELLED` |
| `ERROR` | `ERROR` | `PAST_DUE` |
| `CREATE-PENDING` | `CREATE_PENDING` | `INCOMPLETE` |
| `NON-EXISTING` | `NON_EXISTING` | `UNKNOWN` |
| *(absent)* | `UNKNOWN` | `UNKNOWN` |

`ERROR` becomes `PAST_DUE` rather than a cancellation because a schedule the gateway could not charge is a
schedule that still exists; treating it as ended would revoke access the subscriber may yet pay for.

### What a Bankart callback can and cannot say

Producible: `CheckoutCompleted`, `PaymentSucceeded`, `PaymentFailed`, `SubscriptionCreated`,
`SubscriptionRenewed`, `SubscriptionCancelled`, `Refunded`, `ChargebackOpened`, `ChargebackReversed`,
`UnknownEvent`.

**Never producible from a webhook** — one callback schema, no `SCHEDULE` transaction type, nothing in the
payload that carries the fact: `SubscriptionExpired`, `DunningExhausted`, `TrialStarted`, `TrialEnding`,
`SubscriptionPlanChanged`. A consumer that needs any of them asks `reconcile`.

- A **paused** schedule arrives with nothing saying why, so it becomes `UnknownEvent("schedule-paused")`
  rather than a `SubscriptionCancelled` that would revoke access the subscriber has not lost.
- **`PREAUTHORIZE` and `INCREMENTAL-AUTHORIZATION`** become `UnknownEvent` (`"OK:PREAUTHORIZE"`,
  `"OK:INCREMENTAL_AUTHORIZATION"` — the enum name, not the hyphenated wire value): an authorization is not
  money moving, and calling it `PaymentSucceeded` would have a consumer ship goods against a hold.
- **The first charge of a debit-with-register schedule is indistinguishable from a renewal.** Both arrive as
  a `DEBIT` callback carrying `scheduleData`; it is read as `SubscriptionRenewed`, which carries the new
  expiry — the thing a licence gate actually needs. Where `scheduledAt` is missing or unparseable there is no
  new expiry to carry, and it degrades to `PaymentSucceeded` with the `subscriptionRef` set rather than
  inventing a date.

### Refunds and customers

`refund` refuses two shapes by name, as `PaymentProviderException`, before any request is sent:

- **A full refund with a null amount.** Bankart's refund requires an explicit amount and currency; there is
  no "refund everything". The refusal says to read the charged amount back with `statusByUuid` first.
- **A request with no `merchantReference`.** `merchantTransactionId` is required, and it is also the
  idempotency handle that stops a retry paying the buyer back twice.

A refund the gateway itself declines is different, and surfaces as `PaymentDeclinedException`.

`Customer` has **no VAT field** — nothing in Bankart's customer object holds one, and `nationalId` is a
personal identifier rather than a business one — so a core `Customer.vatNumber` is dropped, documented at
the point it is dropped. A single `name` is **split on the last space**, which is right for
"Ана Петровска" and wrong for a compound surname; the guess is stated in the code rather than hidden.

### Where the sources disagree

The OpenAPI specification and the prose documentation contradict each other, and each contradicts itself.
Every entry below is settled in code with a comment naming which source won, and all but the last three
carry a test that pins the reading.

| The disagreement | How it is read |
|---|---|
| `ScheduleResponse` declares `additionalProperties: false`, yet every error example sends `errorMessage`/`errorCode` (`7040`, `7070`) | Both fields are read; the schema is taken to be wrong about its own examples |
| `ScheduleResponse` declares `registrationId`; the examples send `registrationUuid` | Both spellings are read |
| `NON-EXISTING` is sent by `/schedule/{apiKey}/start`'s own success example and is absent from the spec's `ScheduleStatus` enum | Declared anyway, mapped to `UNKNOWN` |
| `periodLength` is an `integer` on the embedded object and a `number` on `StartSchedule`/`UpdateSchedule` | The narrower of the two, in both places |
| `OptionsResponse.options` is declared an array of `{key, value}`; the endpoint's own 200 example returns a map | Both shapes accepted, by `OptionsDeserializer` |
| `OptionsResponse` declares `error`; the endpoint's own error example sends `errorMessage` | Both spellings read |
| `/options` is the only operation the specification marks as needing no authentication | Basic auth and the signature are sent anyway — an endpoint that ignores them costs nothing, and one that does not is a 401 nobody would explain |
| `payByLinkData` is `{payByLink, sendViaEmail}` in the OpenAPI schema and `{expiresAt, cancelUrl}` in the prose table and example | The prose is modelled; the schema looks stale |
| `/payByLink/{id}/cancel` appears in no path definition in either source — only inside an example `cancelUrl` value | Nothing calls it; PARKED below |
| `payByLinkData` is declared on `StatusResponse` and not on `TransactionResponse` | Modelled where it is declared, with no field invented on the other |
| `StatusResponse.schedules` is a map whose keys the specification never explains | Passed through untouched |
| Of the `7xxx` schedule codes, only `7040` and `7070` appear anywhere in either source | Only those two are named; the range is not guessed at |
| The prose callback table stops at `PAYOUT`; the OpenAPI enum also carries `INCREMENTAL-AUTHORIZATION`, `DISPUTE` and `DISPUTE-REVERSAL` | The machine-readable source wins; all thirteen are modelled |
| `IbanData` (request) and `ReturnIbanData` (response) look alike | Two schemas, two names — the specification keeps them separate and so does this |

## AgentaOS — `payments-agentaos`

Estonian merchant-of-record billing platform, Stripe-backed on the card rail (`stripeSubscriptionId` and
`stripeCustomerId` appear on its own objects) with an on-chain x402 rail alongside.

**There is no API reference.** No page exists at `agentaos.ai/docs`, `/api`, `docs.agentaos.ai` or
`developers.agentaos.ai`. The open-source TypeScript SDK `@agentaos/pay` (2.1.0,
`github.com/AgentaOS/agentaos`, `packages/pay/src`) is the entire specification, and it states what a client
SENDS rather than what the server ACCEPTS. Everything in this adapter not read verbatim in that source is
`@Provisional` with its evidence named, and the inventory is asserted by a test.

**Base URL** `https://api.agentaos.ai`. The version segment is in the path: every route begins
`/api/v1/gateway/`.

**Auth.** `x-api-key: <key>` for keys prefixed `sk_live_` / `sk_test_`; a three-part dotted JWT goes as
`authorization: Bearer <token>` instead.

**Request bodies are camelCase and untransformed; responses arrive snake_case.** The SDK converts responses
client-side and explicitly does not transform requests. This asymmetry is real and is the easiest thing here
to get wrong.

**Idempotency.** An `idempotency-key` header on every POST, never on GET or DELETE.

**Pagination.** `limit` + `offset` in; `{items, total, has_more}` out.

**Amounts — inconsistent by resource, on purpose or otherwise.** Checkouts, payment links, transactions and
invoices carry `amount` as a **decimal number in major units**. Subscriptions and plan-change fields carry
**integer minor units** (`unitAmountMinor`, `amountMinor`). Webhook `amount` on checkout and send events is
a **string**. Currencies: **EUR and USD** only.

**Webhooks.** Header `x-agentaos-signature`, value `t=<unix seconds>,v1=<hex>`. HMAC-SHA256, hex, over the
string `<timestamp>.<raw body>`. Default tolerance 300 seconds, rejecting a FUTURE timestamp as well as a
stale one. A 2xx acknowledges; the body is not read.

**Event catalogue** (from `types.ts`): `checkout.session.completed`, `send.completed`, `send.failed`,
`subscription.created`, `subscription.renewed`, `subscription.payment_failed`, `subscription.updated`,
`subscription.canceled`. **The README documents only the first three** — the five `subscription.*` events
exist in the types file alone, which is why they are provisional.

**Refunds do not exist in the SDK.** No refund, reverse or reversal method anywhere in `packages/pay/src`;
the changelog says cancellation issues none. The adapter therefore does not declare `REFUND` and refuses
rather than inventing an endpoint.

**Sandbox: UNKNOWN.** There is no separate host — `sk_test_` and `sk_live_` keys both hit
`https://api.agentaos.ai`, and test mode is signalled only by the key prefix and a `livemode` boolean on
webhook payloads. Whether a test key reaches a distinct backend or merely flags the same one is not stated
anywhere found.

**Commercial facts, for a consumer weighing adoption.** Pro €49/mo + 4.0% + €0.40 per card sale;
pay-as-you-grow 4.5% + €0.50; chargebacks a flat €35 per dispute regardless of outcome; new accounts held at
a 10–15% reserve released after 90 days clean with disputes under 0.5%; Estonian law, Harju County Court.

### The surface

`AgentaOsClient`, `AgentaOsConfig`, `AgentaOsEventId`, `AgentaOsEventType`, `AgentaOsPaymentProvider`,
`AgentaOsWebhookVerifier`, `AuthMode`, `SubscriptionStatusMapping`; model `BillingInterval`,
`CancelSubscriptionResult`, `Checkout`, `CheckoutField`, `CheckoutFieldType`, `CheckoutStatus`,
`CreateCheckoutRequest` (+ `Builder`), `CreatePaymentLinkRequest` (+ `Builder`), `Customer`, `LinkType`,
`Page<T>`, `PaymentLink`, `PaymentLinkStatus`, `PendingPlanChange`, `SellerMode`, `Subscription`;
internal `Json` (+ `Json.SyntaxException`) and `Amounts`.

Every import in the module is `java.*`, `javax.crypto.*` or `net.aetherealtech.*` — the JDK and the SPI, and
nothing else. That is the zero-dependency claim, checkable by grep.

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
That is the SDK's shape, not a tidying opportunity. `limit` outside 1–100 and a negative offset are refused
before the request.

### What `AgentaOsPaymentProvider` declares

`HOSTED_CHECKOUT`, `RECURRING_CHECKOUT`, `MACHINE_PAYMENT_URL`, `WEBHOOK_SIGNATURE`, `SUBSCRIPTIONS`,
`CANCEL_AT_PERIOD_END`, `RECONCILE`, `PLAN_CHANGE`.

**Not `REFUND`**: there is no refund, reverse or reversal anywhere in `packages/pay/src`, so `refund` throws
`UnsupportedCapabilityException` rather than inventing an endpoint. **Not `TOKENIZATION`**: no instrument
is stored or reused through this API.

**Status mapping** (`SubscriptionStatusMapping.toSpi`, on the lower-cased, trimmed value; `null` is
`UNKNOWN` before the switch):

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

`unpaid` is `EXPIRED` because it is the end of dunning rather than a decision anyone took.
`dunningIsOver(status)` is defined over the SPI value, not the wire one — it is true for anything mapping to
`EXPIRED` or `CANCELLED`, which is `unpaid`, `canceled` **and** `incomplete_expired`.

`reconcile` pages `listSubscriptions` because the SDK exposes no single-subscription read. It gives up at
**100 pages** with `reconcile_exhausted` rather than following `has_more` forever, since a server that never
stops saying "more" would otherwise hang the caller instead of failing it.

### The event id AgentaOS does not send

AgentaOS payloads carry no event id at all, and the SPI requires one to dedupe on. `AgentaOsEventId`
synthesises it: `"agentaos_"` + the first **32** lower-case hex characters of
`SHA-256(type + "\n" + resourceId + "\n" + signatureTimestamp)`, UTF-8, with a null type or resourceId read
as `""`.

`resourceId` is `data.sessionId` for `checkout.session.completed`, `data.transactionId` for `send.*`,
`data.id` for every `subscription.*`, and null for anything unmodelled or unparseable.

`occurredAt` is **the signature timestamp** — the only time the payload carries. `acknowledgement` is the
empty string: AgentaOS wants a 2xx and does not read the body.

**The dedupe assumption is that a redelivery re-sends the timestamp it first signed with.** If AgentaOS
re-signs a retry with a fresh `t=`, the same event yields a different id and a consumer's idempotency key
stops working. Nothing in the SDK settles it, which is why the type is `@Provisional` — see FUTURE 2.

### Event mapping

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

`AT_PERIOD_END` on a plan change is hard-coded because a `pendingPlan` is by definition pending; a change
that had already taken effect would not be pending.

**No retry loop.** One `HttpClient.send`, no backoff. A timeout, a dropped connection or an interruption is
a `PaymentProviderException` with `outcomeUnknown() == true` and a message saying to ask what happened
rather than to send it again — see CUT below.

**Webhook signature.** `x-agentaos-signature`, `t=<unix seconds>,v1=<hex>`, HMAC-SHA256 over
`<timestamp>.<raw body>`, compared with `MessageDigest.isEqual`. Default tolerance 300 seconds, refusing a
future timestamp as well as a stale one. A malformed header and an unparseable `t` are distinct refusal
reasons. **Both camelCase and snake_case bodies are read** — `Json.withCamelAliases` adds camel keys with
`putIfAbsent` over `data` and `pendingPlan` only, so a merchant's own metadata keys are never rewritten.

**Amounts.** Only **EUR and USD** have a minor-unit exponent stated anywhere in the SDK. On a client call an
unknown currency is a refusal (`unsupported_currency`); on a webhook the amount is simply **omitted** and
the event still emitted, because dropping a price is recoverable and scaling MKD or JPY by 100 is not.

### The provisional inventory

Thirteen elements, each carrying a sentence naming the evidence it rests on. `ProvisionalInventoryTest`
walks the module's compiled classes, asserts every `@Provisional` states a non-blank evidence, and pins the
count at 13 — so one added without thinking fails the build rather than joining quietly.

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

## Consumers

| Consumer | Adapter | State |
|---|---|---|
| **kapar** (`kapar.net`) | `payments-bankart` | **Dormant.** No public endpoint calls a provider yet; the module is gated on NLB onboarding for real Bankart credentials. Its own `net.kapar.payments` SPI predates this one and is a candidate for replacement by `payments-core` — see FUTURE 5. |
| **Composure** | `payments-agentaos` | **Planned**, and currently reviewing this SPI. A subscription lifecycle driving a licence gate. The SPI's subscription events, `EffectiveTiming` and `reconcile` are shaped by this consumer's needs, and FUTURE 4 and the parked `listSubscriptions` both wait on its answers. |

Neither consumer is in production against a live gateway.

## Coverage gates

Per module, in each module's own pom, as ratchets.

| Module | Floor (line / branch) | Achieved | Tests |
|---|---|---|---|
| `payments-core` | 90% / 80% | 99.4% / 97.2% | 206 |
| `payments-bankart` | 90% / 80% | 96.7% / 93.2% | 201 |
| `payments-agentaos` | 90% / 80% | 99.0% / 93.7% | 221 |

No test reaches a live gateway. Bankart's are WireMock against fixtures copied from the spec's own
examples; AgentaOS's are WireMock plus signature vectors computed independently of the class under test.

# FUTURE

Numbered, each with why it waits and who can move it. **CUT** = decided against, do not re-propose.
**PARKED** = may return, on the stated condition.

1. **Verify `payments-bankart` against a live sandbox.** Every wire fact here is read from documentation and
   an OpenAPI file; none has been exchanged with a running gateway. *Waits on:* sandbox credentials, which
   are not self-service — the gateway administrators issue them by hand, and for kapar that follows NLB
   onboarding. *Owner:* the operator. Until then the README's "Open questions" is the honest statement of
   what is assumed.

2. **Verify `payments-agentaos` against a real account.** The whole adapter is written from a client SDK's
   source, and a client tells you what it sends rather than what the server accepts. What a live account
   would settle, and nothing else can:

   - **Whether a redelivery re-signs with a fresh `t=`.** The synthesised event id is derived from the
     signature timestamp, so a re-signed retry produces a different id and a consumer's dedupe stops
     working. This is the single most consequential unknown here.
   - **Whether webhook bodies are camelCase or snake_case.** Both are read today, which is the defensive
     answer and not an answer.
   - **Whether `GET /api/v1/gateway/subscriptions/{id}` exists.** The SDK exposes only a list, so
     `reconcile` pages it; a single read would make the operation O(1) instead of O(pages).
   - **The `x-agentaos-signature` header name.** It appears in an example app and the package README, never
     in `packages/pay/src` — the verifier takes the signature as a parameter and never reads a header.
   - **Whether `metadata` survives from checkout creation to `checkout.session.completed`.** The merchant
     reference rides in it; if it does not round-trip, it arrives null.
   - **The `limit` default** — the types file says 10, the README says 20, the wallet CLI says 10.
   - **The minor-unit exponent for anything but EUR and USD.** Today an unknown currency is refused on a
     client call and omitted on a webhook rather than scaled by a guessed 100.
   - **Whether `x402Url` is returned by a given deployment.** The types declare it; the wallet package's own
     comment says it may not be in the published types yet and casts around it.
   - Plus, still open from the first reading: whether test mode is a separate backend or a flag on the same
     one; the true event catalogue and its retry policy; the numeric rate limit (a `RateLimitError` exists,
     no published figure does); whether a refund API exists outside `packages/pay`; and the JSON error-body
     shape beyond the `message`/`error` fields the client reads.

   Most of these are `@Provisional` in the code with their evidence named — see the inventory above. The
   ones that are not (test mode, the rate limit, the error-body shape) are absences rather than assumptions:
   nothing was modelled on them. *Waits on:* an AgentaOS account. *Owner:* the operator.

3. **Replace Jackson in `payments-bankart` with an internal reader.** It would take the reactor to zero
   runtime dependencies across all three artifacts. *Waits on:* the Bankart wire format being worth it — it
   is large, deeply nested, polymorphic on `_TYPE`, and carries fields whose JSON type differs between the
   docs' own examples (an amount that is a string in one and a number in another, a code typed as a number
   and sent as `"2016"`). Hand-writing that reader is a real cost, and it should follow FUTURE 1 rather than
   precede it: rewriting a parser for a format nobody has yet exchanged with a live gateway is rewriting a
   guess. *Owner:* whoever picks it up after the sandbox verification.

4. **A `PlanChangeQuote` operation.** AgentaOS exposes `GET .../plan-change/preview` then
   `POST .../plan-change` — a quote-then-commit pair whose quote carries proration money
   (`dueTodayMinor`, `nextInvoiceMinor`, a `prorationDate` in Unix seconds that must be passed back). The
   SPI models the plan-change EVENT but no operation, because modelling the quote means modelling proration,
   which is the one thing `SubscriptionPlanChanged` deliberately refuses to carry. Bankart has no equivalent
   and would refuse it. *Waits on:* Composure's review answering whether it needs it. *Owner:* Composure.

5. **Move kapar onto `payments-core`.** kapar has its own `net.kapar.payments` interface —
   `PaymentProvider`, `PaymentIntent`, `RedirectTarget`, `NotificationOutcome`, `PaymentProviderRegistry` —
   written before this SPI existed and narrower than it (no subscription events, no capabilities, no
   reconcile). Keeping two vocabularies for one concept is the cost. *Waits on:* kapar's payments wave
   waking up at all, which is gated on NLB. *Owner:* kapar.

6. **Consumer note, not a recommendation: AgentaOS's Master Services Terms (Section 21) prohibit
   "standalone marketing/SEO/ads services except approved Service Packages"**, and separately prohibit
   "offerings with no bona fide digital Product". A classifieds operator selling featured-listing placements
   or promotion products would need those products evaluated against that clause before adopting AgentaOS as
   its merchant of record. This is a fact recorded so a consumer can weigh it, not advice about what to do
   with it. *Owner:* the operator, if and when a consumer proposes AgentaOS for promotion products.

**CUT** — decided against, do not re-propose:

- **A `mapper` package, or a `PaymentEventMapper` per adapter.** Each adapter's provider class owns its own
  translation; a shared mapping layer would need to know every provider's shape, which is the coupling the
  SPI exists to remove.
- **Retrying a POST inside the client.** The AgentaOS SDK retries 5xx and network failures automatically;
  this adapter does not. A library that silently retries a payment POST is a library that double-charges,
  and `PaymentProviderException.outcomeUnknown()` exists precisely so the caller can recover by asking
  rather than by repeating.
- **A tenant or account filter on anything.** There is none, by design; scoping is the consumer's.

**PARKED** — may return, on the stated condition:

- **A `listSubscriptions` operation.** Returns if a consumer needs to sweep rather than to look one up.
  AgentaOS's only subscription read IS a list, so it would cost nothing there; Bankart would have to refuse
  it, since the schedule API reads one schedule at a time. *Condition:* Composure's review asking for it.
- **Bankart's dispute API** (`/dispute/{apiKey}/{accept|metadata|upload-evidence|submit-evidence}/{uuid}`).
  Returns when a consumer actually contests a chargeback; today chargebacks are surfaced as events and
  nothing acts on them.
- **Pay-by-link cancellation.** The prose docs show a `cancelUrl` pointing at
  `/api/v3/payByLink/{id}/cancel`, a route absent from the OpenAPI spec's paths entirely. Returns when its
  verb and shape can be observed rather than inferred from a field in an example.
