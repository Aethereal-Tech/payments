# Bankart adapter

## Purpose

`payments-bankart` is a client and `PaymentProvider` for white-label IXOPAY, as Bankart exposes it. Bankart's
`gateway.bankart.si/documentation/apiv3` and IXOPAY's `documentation.ixopay.com` describe the same API. This
capability records the wire facts, the client surface, the capabilities the provider declares and refuses, the
status and event mappings, the refusal rules, and the specific points where the OpenAPI specification and the
prose documentation disagree with each other.

## Requirements

### Requirement: Endpoints and base URL
The client SHALL call `POST /transaction/{apiKey}/{debit|preauthorize|capture|void|refund|register|deregister|payout|incrementalAuthorization}`,
`POST /schedule/{apiKey}/start`, `POST /schedule/{apiKey}/{scheduleId}/{update|pause|continue|cancel}`,
`GET /schedule/{apiKey}/{scheduleId}/get`, `GET /status/{apiKey}/getByUuid/{uuid}`,
`GET /status/{apiKey}/getByMerchantTransactionId/{id}`, and `POST /options/{apiKey}/{optionsName}`, against base
URL `https://gateway.bankart.si/api/v3`.

#### Scenario: A transaction operation targets the transaction path
- **WHEN** a `debit` is sent
- **THEN** it is a `POST` to `/transaction/{apiKey}/debit` against `https://gateway.bankart.si/api/v3`

### Requirement: Authentication is HTTP Basic with the API key as a path segment
Every request SHALL carry `Authorization: Basic base64(user:password)`. The API key SHALL travel as a PATH
segment, never as a header.

#### Scenario: The API key never appears as a header
- **WHEN** any request is sent
- **THEN** the API key appears only in the request path, and `Authorization` carries HTTP Basic credentials

### Requirement: The signature is a bare-Base64 HMAC-SHA512 over five newline-joined components
`X-Signature` SHALL carry a **bare Base64** value — not `Gateway KEY:SIG` — computed as HMAC-SHA512, keyed with
the connector's shared secret, over five components joined by a single `\n`:

```
METHOD
sha512hex(request body, exactly as transmitted)
Content-Type header value
Date header value (X-Date takes precedence)
request URI
```

The inner body hash SHALL be **hex**; the outer HMAC SHALL be **binary → Base64, not hex**. Signing is a
per-connector setting ("API: Enable Request Signing"), so a connector may legitimately send an unsigned
callback; the verifier here never accepts one.

**Where the discrepancy was settled:** the OpenAPI specification does not mention signatures at all — its only
`securityScheme` is `basicAuth`. The bare-Base64 form comes from the prose documentation's own worked example,
which reproduces. Pinned by a test naming that source.

#### Scenario: The outer HMAC is Base64, not hex
- **WHEN** the five-component message is signed
- **THEN** the inner body digest is hex-encoded SHA-512
- **AND** the outer HMAC-SHA512 over the joined message is Base64-encoded, not hex

#### Scenario: An unsigned connector callback is never accepted
- **WHEN** a connector has request signing disabled and sends an unsigned callback
- **THEN** the verifier here still requires a valid signature and rejects the callback

### Requirement: Amounts are decimal strings; MKD is supported at 2 decimals
Amounts SHALL be decimal strings (e.g. `"15.75"`), at the precision ISO 4217 states for the currency. **MKD is
supported, at 2 decimals.**

#### Scenario: An MKD amount is sent at 2 decimals
- **WHEN** an amount in MKD is sent
- **THEN** it is a decimal string at 2 decimal places

### Requirement: One Callback notification shape, acknowledged with the literal body OK
Every notification SHALL be one `Callback` shape, POSTed to the transaction's `callbackUrl`, with `result` ∈
{OK, PENDING, ERROR} and `transactionType` ∈ {DEBIT, CAPTURE, DEREGISTER, PREAUTHORIZE, REFUND, REGISTER, VOID,
CHARGEBACK, CHARGEBACK-REVERSAL, PAYOUT, INCREMENTAL-AUTHORIZATION, DISPUTE, DISPUTE-REVERSAL}. A notification
SHALL be acknowledged with **HTTP 200 and the literal body `OK`** — anything else and the gateway retries
immediately, then at 1, 5, 15, 60, 120, 180 and 720 minutes, then every 24 hours for 7 days.

#### Scenario: Acknowledging with anything but literal OK triggers the retry schedule
- **WHEN** a notification is answered with anything other than HTTP 200 and the literal body `OK`
- **THEN** the gateway retries immediately, then at 1, 5, 15, 60, 120, 180 and 720 minutes, then every 24 hours
  for 7 days

### Requirement: There is no SCHEDULE notification type
`Callback` SHALL have no `SCHEDULE` transaction type. A recurring charge SHALL arrive as an ordinary transaction
callback with a `scheduleData` field populated, with no discriminator distinguishing "schedule paused" from
"schedule cancelled" from "renewal failed". Subscription state SHALL be inferred from
`scheduleData.scheduleStatus` alongside the transaction's own `result`, or polled with `reconcile`.

This is the single most consequential fact about Bankart as a subscription provider.

#### Scenario: A recurring charge arrives as an ordinary transaction callback
- **WHEN** a scheduled charge occurs
- **THEN** it arrives as a `DEBIT` callback (or another ordinary transaction type) with `scheduleData` populated,
  never as a distinct `SCHEDULE` notification

### Requirement: returnData is a oneOf discriminated on _TYPE, with IBAN covering SEPA
`returnData` SHALL be a `oneOf` discriminated on `_TYPE` ∈ {`cardData`, `phoneData`, `ibanData`, `walletData`}.
There SHALL be no separate SEPA variant; IBAN covers it.

#### Scenario: A SEPA payment is read as ibanData
- **WHEN** a SEPA payment's `returnData` is read
- **THEN** it is read under the `ibanData` `_TYPE`, since no separate SEPA variant exists

### Requirement: The status API is rate-limited to 5 requests per minute per identifier
The status API SHALL be rate-limited to **5 requests per minute per `transactionUuid` or
`merchantTransactionId`**, answering HTTP 429 beyond it. This is stated in the prose docs only; the OpenAPI spec
states no limit anywhere. A poller SHALL respect it.

#### Scenario: A sixth status request within a minute is refused
- **WHEN** a sixth status lookup for the same `transactionUuid` is made within one minute
- **THEN** the gateway answers HTTP 429

### Requirement: Sandbox credentials are issued by hand, not self-service
The sandbox host `https://bankart.paymentsandbox.cloud/api/v3` SHALL have its own OpenAPI document at
`/Schema/V3/OpenApiSpecification.yml`. Sandbox credentials SHALL NOT be self-service — they are issued by hand by
the gateway administrators or an integration engineer.

#### Scenario: Sandbox credentials cannot be obtained by self-service signup
- **WHEN** sandbox access is requested
- **THEN** credentials are issued by hand by the gateway administrators or an integration engineer, not through
  a self-service flow

### Requirement: BankartClient maps one method per gateway operation
`BankartClient` SHALL return `TransactionResponse` from every transaction operation and `ScheduleResponse` from
every schedule one:

| Method | Call |
|---|---|
| `debit`, `preauthorize`, `capture`, `voidTransaction`, `refund`, `register`, `deregister`, `payout`, `incrementalAuthorization` | `POST /transaction/{apiKey}/{operation}` |
| `startSchedule` | `POST /schedule/{apiKey}/start` |
| `updateSchedule`, `pauseSchedule`, `continueSchedule`, `cancelSchedule` | `POST /schedule/{apiKey}/{scheduleId}/{update\|pause\|continue\|cancel}` |
| `showSchedule` | `GET /schedule/{apiKey}/{scheduleId}/get` |
| `options` | `POST /options/{apiKey}/{optionsName}` |
| `statusByUuid`, `statusByMerchantTransactionId` | `GET /status/{apiKey}/getBy…` |
| `startCheckout`, `startRegistration` | `debit` / `register` narrowed to a `RedirectResult` |

`debit` SHALL refuse a `captureInMinutes` by name rather than sending it: the field applies to `preauthorize`,
and a gateway that ignores it silently would leave a caller believing in a delayed capture that never happens.

Schedule and options types: `Schedule`, `ScheduleData`, `ScheduleStatus`, `SchedulePeriodUnit`,
`StartScheduleRequest`, `UpdateScheduleRequest`, `ContinueScheduleRequest`, `ScheduleResponse`,
`IncrementalAuthorizationRequest`, `OptionsRequest`, `OptionsResponse`, `Option`, `PayByLink`, `PayByLinkData`.

#### Scenario: debit refuses a captureInMinutes value
- **WHEN** `debit` is called with a `captureInMinutes` value set
- **THEN** the client refuses by name rather than silently sending or dropping the field

### Requirement: returnData is a sealed ReturnData with four variants
`returnData` SHALL be modelled as a sealed `ReturnData` permitting `CardData`, `ReturnPhoneData`,
`ReturnIbanData` and `ReturnWalletData`, deserialized by `ReturnDataDeserializer` on the `_TYPE` discriminator.
`cardData()` — `Optional<CardData>`, on `Notification`, `TransactionResponse` and `StatusResponse` alike — SHALL
be kept as the card-only reading, with the "or it was not a card" case made explicit.

This is the one source change a `bankart-gateway:0.1.0` caller must make: `returnData()` was `CardData` and is
now `ReturnData`.

#### Scenario: A non-card returnData no longer throws a class-cast failure
- **WHEN** `returnData` carries `walletData`
- **THEN** it deserializes into `ReturnWalletData` rather than failing as an unexpected `CardData`

#### Scenario: cardData() answers empty for a non-card payment
- **WHEN** `cardData()` is called on a response whose `returnData` is not `cardData`
- **THEN** it answers `Optional.empty()`

### Requirement: BankartPaymentProvider declares eight capabilities and asserts two absences
`BankartPaymentProvider` SHALL declare `HOSTED_CHECKOUT`, `RECURRING_CHECKOUT`, `WEBHOOK_SIGNATURE`, `REFUND`,
`SUBSCRIPTIONS`, `PLAN_CHANGE`, `RECONCILE`, `TOKENIZATION`.

It SHALL NOT declare `CANCEL_AT_PERIOD_END`: `cancelSchedule` has no deferral — there is no "cancel at the end of
the paid period" anywhere in the schedule API, so the capability would be a promise the gateway cannot keep. It
SHALL NOT declare `MACHINE_PAYMENT_URL`: no non-browser payment rail exists here. Both absences are asserted, not
merely omitted.

`reconcile(scheduleId)` SHALL read `GET /schedule/{apiKey}/{scheduleId}/get` — the direct answer to a webhook
schema that cannot express subscription state.

#### Scenario: cancelSubscription at period end is refused
- **WHEN** `cancelSubscription(ref, true)` is called
- **THEN** `UnsupportedCapabilityException` is raised, since `CANCEL_AT_PERIOD_END` is not declared

#### Scenario: reconcile reads the schedule directly
- **WHEN** `reconcile(scheduleId)` is called
- **THEN** it issues `GET /schedule/{apiKey}/{scheduleId}/get`

### Requirement: ScheduleStatus maps to SubscriptionStatus with ERROR read as PAST_DUE
`BankartPaymentProvider.subscriptionStatus` SHALL map `ScheduleStatus`, matched case-insensitively, with `null`
and anything unrecognised landing on `UNKNOWN`:

| Wire | Enum | SPI |
|---|---|---|
| `ACTIVE` | `ACTIVE` | `ACTIVE` |
| `PAUSED` | `PAUSED` | `PAUSED` |
| `CANCELLED` | `CANCELLED` | `CANCELLED` |
| `ERROR` | `ERROR` | `PAST_DUE` |
| `CREATE-PENDING` | `CREATE_PENDING` | `INCOMPLETE` |
| `NON-EXISTING` | `NON_EXISTING` | `UNKNOWN` |
| *(absent)* | `UNKNOWN` | `UNKNOWN` |

`ERROR` SHALL become `PAST_DUE` rather than a cancellation because a schedule the gateway could not charge is a
schedule that still exists; treating it as ended would revoke access the subscriber may yet pay for.

#### Scenario: A wire ERROR status maps to PAST_DUE, not CANCELLED
- **WHEN** `scheduleData.scheduleStatus` is `ERROR`
- **THEN** the SPI status is `PAST_DUE`, not `CANCELLED`, so access is not revoked from a subscriber who may yet
  pay

#### Scenario: An absent status maps to UNKNOWN
- **WHEN** `scheduleData.scheduleStatus` is absent or unrecognised
- **THEN** the SPI status is `UNKNOWN`

### Requirement: A Bankart callback can produce ten events and never five others
A Bankart callback SHALL be able to produce `CheckoutCompleted`, `PaymentSucceeded`, `PaymentFailed`,
`SubscriptionCreated`, `SubscriptionRenewed`, `SubscriptionCancelled`, `Refunded`, `ChargebackOpened`,
`ChargebackReversed`, `UnknownEvent`.

It SHALL NEVER produce `SubscriptionExpired`, `DunningExhausted`, `TrialStarted`, `TrialEnding`, or
`SubscriptionPlanChanged` — one callback schema carries no `SCHEDULE` transaction type and nothing in the
payload states any of these facts. A consumer that needs one of them calls `reconcile`.

- A **paused** schedule arrives with nothing saying why, so it becomes `UnknownEvent("schedule-paused")` rather
  than a `SubscriptionCancelled` that would revoke access the subscriber has not lost.
- **`PREAUTHORIZE` and `INCREMENTAL-AUTHORIZATION`** become `UnknownEvent` (`"OK:PREAUTHORIZE"`,
  `"OK:INCREMENTAL_AUTHORIZATION"` — the enum name, not the hyphenated wire value): an authorization is not
  money moving, and calling it `PaymentSucceeded` would have a consumer ship goods against a hold.
- **The first charge of a debit-with-register schedule is indistinguishable from a renewal.** Both arrive as a
  `DEBIT` callback carrying `scheduleData`; it is read as `SubscriptionRenewed`, which carries the new expiry —
  the thing a licence gate actually needs. Where `scheduledAt` is missing or unparseable there is no new expiry
  to carry, and it degrades to `PaymentSucceeded` with the `subscriptionRef` set rather than inventing a date.

#### Scenario: A paused schedule becomes an UnknownEvent, not a cancellation
- **WHEN** a callback arrives for a paused schedule
- **THEN** it is read as `UnknownEvent("schedule-paused")`, not `SubscriptionCancelled`

#### Scenario: PREAUTHORIZE never becomes PaymentSucceeded
- **WHEN** a `PREAUTHORIZE` callback arrives
- **THEN** it is read as `UnknownEvent("OK:PREAUTHORIZE")`, never `PaymentSucceeded`

#### Scenario: INCREMENTAL-AUTHORIZATION never becomes PaymentSucceeded
- **WHEN** an `INCREMENTAL-AUTHORIZATION` callback arrives
- **THEN** it is read as `UnknownEvent("OK:INCREMENTAL_AUTHORIZATION")`, never `PaymentSucceeded`

#### Scenario: A first debit-with-register charge reads as SubscriptionRenewed
- **WHEN** a `DEBIT` callback carries `scheduleData` with a parseable `scheduledAt`
- **THEN** it is read as `SubscriptionRenewed`, carrying the new expiry

#### Scenario: A missing scheduledAt degrades to PaymentSucceeded
- **WHEN** a `DEBIT` callback carries `scheduleData` but `scheduledAt` is missing or unparseable
- **THEN** it degrades to `PaymentSucceeded` with `subscriptionRef` set, rather than inventing a date

### Requirement: refund refuses two shapes by name before sending a request
`refund` SHALL refuse, as `PaymentProviderException`, before any request is sent:

- **A full refund with a null amount.** Bankart's refund requires an explicit amount and currency; there is no
  "refund everything". The refusal SHALL say to read the charged amount back with `statusByUuid` first.
- **A request with no `merchantReference`.** `merchantTransactionId` is required, and it is also the idempotency
  handle that stops a retry paying the buyer back twice.

A refund the gateway itself declines SHALL surface as `PaymentDeclinedException`, distinct from a refused shape.

#### Scenario: A null-amount full refund is refused before any request
- **WHEN** `refund` is called for a full refund with a null amount
- **THEN** `PaymentProviderException` is raised naming that the charged amount should be read back with
  `statusByUuid` first, and no request is sent

#### Scenario: A refund with no merchantReference is refused before any request
- **WHEN** `refund` is called with no `merchantReference`
- **THEN** `PaymentProviderException` is raised, and no request is sent

#### Scenario: A gateway-declined refund is a PaymentDeclinedException
- **WHEN** a well-formed refund request is declined by the gateway itself
- **THEN** it surfaces as `PaymentDeclinedException`, not `PaymentProviderException`

### Requirement: Customer carries no VAT field; a name is split on the last space
`Customer` SHALL carry no VAT field — nothing in Bankart's customer object holds one, and `nationalId` is a
personal identifier rather than a business one — so a core `Customer.vatNumber` SHALL be dropped, documented at
the point it is dropped. A single `name` SHALL be split on the last space.

That split is right for "Ана Петровска" and wrong for a compound surname; the guess is stated in the code rather
than hidden.

#### Scenario: Customer.vatNumber is dropped when converting to Bankart's shape
- **WHEN** a core `Customer` with a `vatNumber` is converted to Bankart's customer object
- **THEN** `vatNumber` is dropped, since Bankart's object has no field for it

#### Scenario: A single name is split on the last space
- **WHEN** a `Customer` carries a single `name` field
- **THEN** it is split into first and last name on the last space

### Requirement: OpenAPI/prose disagreements are settled in code and named
Where the OpenAPI specification and the prose documentation disagree with each other, or with themselves, the
disagreement SHALL be settled in code with a comment naming which source won:

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
| `/payByLink/{id}/cancel` appears in no path definition in either source — only inside an example `cancelUrl` value | Nothing calls it — see the parked `bankart-pay-by-link-cancellation` change |
| `payByLinkData` is declared on `StatusResponse` and not on `TransactionResponse` | Modelled where it is declared, with no field invented on the other |
| `StatusResponse.schedules` is a map whose keys the specification never explains | Passed through untouched |
| Of the `7xxx` schedule codes, only `7040` and `7070` appear anywhere in either source | Only those two are named; the range is not guessed at |
| The prose callback table stops at `PAYOUT`; the OpenAPI enum also carries `INCREMENTAL-AUTHORIZATION`, `DISPUTE` and `DISPUTE-REVERSAL` | The machine-readable source wins; all thirteen are modelled |
| `IbanData` (request) and `ReturnIbanData` (response) look alike | Two schemas, two names — the specification keeps them separate and so does this |

Every entry above but the last three SHALL carry a test that pins the reading.

#### Scenario: A schema declaring additionalProperties: false still reads its own documented error fields
- **WHEN** a `ScheduleResponse` error example carrying `errorMessage`/`errorCode` is parsed
- **THEN** both fields are read despite the schema declaring `additionalProperties: false`

#### Scenario: The OpenAPI enum wins over the prose callback table
- **WHEN** an `INCREMENTAL-AUTHORIZATION`, `DISPUTE` or `DISPUTE-REVERSAL` callback is parsed
- **THEN** it is modelled, even though the prose callback table stops at `PAYOUT`
