# payments

A provider-neutral payments SPI for Java, with two adapters under it: **Bankart** (the IXOPAY-based gateway
NLB fronts in the region) and **AgentaOS** (an Estonian merchant-of-record platform with an on-chain rail
alongside the card one).

> **Not affiliated with Bankart, NLB, IXOPAY or AgentaOS.** These are independent, unofficial clients
> written by Aethereal Tech against each provider's published material — for Bankart, its public
> documentation and sandbox OpenAPI specification; for AgentaOS, its open-source TypeScript SDK, which is
> the only statement of that wire format there is. Neither is endorsed, supported, certified or reviewed by
> anyone. The names are used only to say which gateway each artifact speaks to. For contractual or
> certification questions, talk to your integration engineer, not to this repository.

**Requires JDK 25 or newer.**

## Three artifacts

| Artifact | What it gives you | Runtime dependencies |
|---|---|---|
| `payments-core` | The SPI, the model, the events, the refusals, and a recording test double | **none** |
| `payments-bankart` | The Bankart Transaction API v3 client, plus its `PaymentProvider` | Jackson |
| `payments-agentaos` | The AgentaOS gateway client, plus its `PaymentProvider` | **none** |

Take `payments-core` plus the adapter you need. The split exists so that a consumer talking only to AgentaOS
never receives Jackson — which matters more than it sounds, because a consumer already running Jackson 3
cannot have an unwanted Jackson 2 mediated away: they are different coordinates with different package
names, and both simply land on the classpath.

All three are published from one commit at one version. Never mix versions between them.

## Install

Artifacts go to GitHub Packages.

```xml
<dependency>
    <groupId>net.aetherealtech</groupId>
    <artifactId>payments-core</artifactId>
    <version>0.2.0</version>
</dependency>
<dependency>
    <groupId>net.aetherealtech</groupId>
    <artifactId>payments-bankart</artifactId>
    <version>0.2.0</version>
</dependency>
<dependency>
    <groupId>net.aetherealtech</groupId>
    <artifactId>payments-agentaos</artifactId>
    <version>0.2.0</version>
</dependency>

<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Aethereal-Tech/payments</url>
    </repository>
</repositories>
```

**GitHub Packages always requires authentication, and this repository is private, so the token needs more
than the usual `read:packages`.** Anonymous Maven downloads from `maven.pkg.github.com` return 401 for any
repository regardless of visibility — that is a GitHub platform limitation, not a choice made here. On top of
that, because these packages are private, the token must additionally be entitled to the organization's
private packages:

- a **classic** personal access token with **`read:packages` and `repo`**, held by an account with access to
  `Aethereal-Tech/payments`; or
- a **fine-grained** token granted access to this repository.

`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN</password>
    </server>
  </servers>
</settings>
```

The `<id>` must match the `<repository><id>` above.

**From another repository's GitHub Actions**, the workflow's own `GITHUB_TOKEN` is scoped to that repository
and cannot read this one's packages. Either grant the consuming repository read access in this package's
settings, or give the workflow a token of the kind above as a secret. A build that skips this fails while
*resolving the dependency*, not at some later step — the error names the artifact, so it is at least
self-explanatory.

## Upgrading from `bankart-gateway` 0.1.0

`net.aetherealtech:bankart-gateway:0.1.0` was one artifact for one gateway. It stays published under that
name; there will be no 0.2.0 of it. From 0.2.0 the same client lives in `payments-bankart`, under
`payments-core`.

| | 0.1.0 | 0.2.0 |
|---|---|---|
| Coordinates | `net.aetherealtech:bankart-gateway` | `net.aetherealtech:payments-core` + `net.aetherealtech:payments-bankart` |
| Package | `net.aetherealtech.bankart` | `net.aetherealtech.payments.bankart` |

Two source changes beyond the rename, both on the same field:

- **`returnData()` now returns `ReturnData`, not `CardData`.** It is a sealed interface permitting
  `CardData`, `ReturnPhoneData`, `ReturnIbanData` and `ReturnWalletData` — the four `_TYPE` variants the
  gateway actually sends. 0.1.0 read the card one and silently dropped the rest, which is wrong the moment a
  wallet or a direct-debit payment arrives. Switch over it, or keep the old reading with the accessor below.
- **`cardData()` returns `Optional<CardData>`.** It is the card variant of `returnData()` and nothing else,
  so a caller that only ever handled cards changes `returnData()` to `cardData()` and gets back the same
  value it had — now with the "or it was not a card" case made explicit rather than assumed.

Both apply to `Notification`, `TransactionResponse` and `StatusResponse` alike. On `0.x` this is a `feat:`
rather than a break of a promise the version does not make; it is called out here because it is the one
thing a 0.1.0 caller must edit.

Everything else 0.1.0 had is still there, and the client gained the schedule (subscription) API,
`incrementalAuthorization` and the options request.

## The SPI in one page

```java
public interface PaymentProvider {
    String id();
    Set<ProviderCapability> capabilities();
    default boolean supports(ProviderCapability capability);

    RedirectTarget       startCheckout(PaymentIntent intent);
    PaymentEvent         handleWebhook(InboundWebhook webhook);
    SubscriptionSnapshot reconcile(String subscriptionRef);
    SubscriptionSnapshot cancelSubscription(String subscriptionRef, boolean atPeriodEnd);
    RefundReceipt        refund(RefundRequest request);
}
```

**Ask `capabilities()`, do not guess.** Calling an operation whose capability a provider does not declare is
an `UnsupportedCapabilityException` naming the capability — never a null, never a no-op, never a
plausible-looking result that quietly did nothing.

| Capability | Bankart | AgentaOS |
|---|:--:|:--:|
| `HOSTED_CHECKOUT` | yes | yes |
| `RECURRING_CHECKOUT` | yes | yes |
| `WEBHOOK_SIGNATURE` | yes | yes |
| `SUBSCRIPTIONS` | yes | yes |
| `RECONCILE` | yes | yes |
| `PLAN_CHANGE` | yes | yes |
| `REFUND` | yes | **no** — none exists in the SDK |
| `TOKENIZATION` | yes | **no** |
| `CANCEL_AT_PERIOD_END` | **no** — `cancelSchedule` has no deferral | yes |
| `MACHINE_PAYMENT_URL` | **no** — no non-browser rail | yes — the x402 URL |

The two gaps are the ones to design around. **Bankart cannot cancel at the end of a paid period**: its
schedule API has no deferral, so a consumer that owes a subscriber the time they paid for has to hold the
cancellation itself and call `cancelSubscription` when the period ends. **AgentaOS cannot refund**: there is
no refund, reverse or reversal method anywhere in its SDK, and this library refuses rather than guessing at
an endpoint.

**Money** is a `BigDecimal` plus an ISO 4217 code, everywhere. The providers disagree with each other and
with themselves — Bankart takes decimal strings, AgentaOS mixes decimal amounts on checkouts with integer
minor units on subscriptions — and converting is the adapter's job, done once where the resource is known.
`Money` has no arithmetic: adding, prorating and converting are product decisions, and a convenient `plus`
would be making them for you.

**Events are one vocabulary over both providers**, and the sealed interface means an exhaustive switch stops
compiling when a new one appears:

| | |
|---|---|
| `CheckoutCompleted` | the buyer finished and the provider considers it paid |
| `PaymentSucceeded` / `PaymentFailed` | money moved, or one attempt did not |
| `SubscriptionCreated` / `Renewed` / `PlanChanged` / `Cancelled` / `Expired` | the lifecycle |
| `DunningExhausted` | retries are over — **terminal**, unlike `PaymentFailed` |
| `TrialStarted` / `TrialEnding` | where a provider reports them |
| `Refunded`, `ChargebackOpened`, `ChargebackReversed` | audit facts |
| `UnknownEvent` | verified, but not something this SPI models |

Two properties your handler must have, and neither is optional:

1. **Idempotent on `event.eventId()`.** The same event arrives more than once; that is what a retry schedule
   does when an acknowledgement is slow or lost.
2. **Able to move backwards as well as forwards.** An event older than the state you hold is a late
   redelivery, not a contradiction — and a Bankart transaction going from failed to successful for the same
   reference is documented behaviour, not a replay. Let the newest fact win.

Answer the request with `event.acknowledgement()`, **after** the event is durably recorded. Bankart requires
HTTP 200 with the literal body `OK` and retries for seven days on anything else; AgentaOS wants a 2xx and
does not read the body. The right text travels on the event so your handler need not know which provider it
is serving.

Where those two properties are not enough — an endpoint that was down, a provider that documents fewer
events than it sends — `reconcile(subscriptionRef)` answers with the current truth instead of a history.

## What throws and what does not

| Situation | Result |
|---|---|
| The instrument declined | `PaymentDeclinedException` — tell the buyer; `declineCode()` is what to branch on |
| Request rejected, unauthorised, rate-limited, provider 5xx | `PaymentProviderException`, `outcomeUnknown() == false` — safe to correct and retry |
| **Timeout, dropped connection, interruption** | `PaymentProviderException`, **`outcomeUnknown() == true`** — the payment may have gone through. Recover by asking (`reconcile`, or a status lookup on your own reference), never by repeating |
| A webhook did not authenticate | `WebhookVerificationException`; `reason()` names which component failed |
| An operation the provider does not declare | `UnsupportedCapabilityException` |
| A Bankart card decline arriving as `returnType: ERROR` on the low-level client | returned as a `TransactionResponse` — a business outcome, not a thrown failure |

Everything above extends `PaymentException`, so one catch covers the lot. Each adapter's own client stays
public below the SPI with its own richer exception types; reaching for it means accepting those too.

Branch on codes, never on messages. Both providers reserve the right to reword message text.

## Quickstart — Bankart

```java
BankartConfig config = BankartConfig.production(
        "yourApiKey", "yourApiUser", "yourApiPassword", "yourSharedSecret");

PaymentProvider payments = new BankartPaymentProvider(config);

RedirectTarget target = payments.startCheckout(
        PaymentIntent.builder("order-2026-0001")
                .amount(Money.of("9.99", "EUR"))
                .description("Example Product")
                .urls("https://shop.example/checkout/success",
                      "https://shop.example/checkout/cancel",
                      "https://shop.example/checkout/error",
                      "https://shop.example/payments/bankart/callback")
                .build());

// Persist this BEFORE redirecting. The webhook that decides whether the payment happened arrives
// independently of the buyer's browser, and may well arrive first.
orders.markAwaitingPayment(orderId, target.checkoutRef());

response.sendRedirect(target.redirectUrl());
```

**The synchronous answer is not the outcome.** For any redirect flow the gateway's immediate response only
says the transaction started; the documentation is explicit that *"for the final result you should only
trust the notification, NOT the back redirection."* A buyer who lands on your success URL has not
necessarily paid.

`BankartClient` remains available underneath for everything the SPI does not cover — preauthorize, capture,
void, payout, register/deregister, incremental authorization, the schedule API, the options request, and
the status lookups.

**Status lookups are rate-limited to 5 requests per minute per transaction** (per `transactionUuid` or
`merchantTransactionId`), answered with HTTP 429 beyond that. Do not poll faster.

## Quickstart — AgentaOS

```java
AgentaOsConfig config = AgentaOsConfig.of("sk_live_…", "whsec_…");

PaymentProvider payments = new AgentaOsPaymentProvider(config);

// A subscription is created by the buyer paying a subscription payment LINK, so a recurring intent
// names the link rather than a cadence. A one-off intent names an amount instead.
RedirectTarget target = payments.startCheckout(
        PaymentIntent.builder("licence-2026-0001")
                .planRef("pl_…")
                .recurrence(Recurrence.monthly())
                .customer(Customer.withEmail("buyer@example.com"))
                .urls("https://app.example/billing/done",
                      "https://app.example/billing/cancelled",
                      null,
                      "https://app.example/webhooks/agentaos")
                .build());

licences.awaitActivation(licenceId, target.checkoutRef());

// The same purchase, offered to something that is not a browser.
target.machinePayment().ifPresent(url -> agents.offerX402(licenceId, url));
```

## Handling a webhook

The same code serves either provider.

```java
@PostMapping("/webhooks/{provider}")
public ResponseEntity<String> webhook(
        @PathVariable String provider,
        @RequestBody byte[] body,                  // RAW bytes — see below
        @RequestHeader Map<String, String> headers,
        HttpServletRequest request) {

    PaymentProvider payments = registry.get(provider);

    PaymentEvent event = payments.handleWebhook(
            InboundWebhook.post(body, requestUri(request), headers));

    // Persist FIRST, acknowledge SECOND. Acknowledging before the state is durable turns a crash
    // into a payment you will never hear about again.
    ledger.apply(event);

    return ResponseEntity.ok(event.acknowledgement());
}
```

**The body must be the raw bytes as received.** Every signature scheme here covers the exact octets the
provider transmitted, so a body that has been parsed and re-serialised will not verify however identical it
looks — in Spring that means `byte[]`, not a `@RequestBody` DTO, and in Express it means
`express.raw({ type: 'application/json' })`. This is the single most common way a working integration is
reported as broken.

A `WebhookVerificationException` means the request did not authenticate. Return a non-2xx and change
nothing: an unverifiable webhook is indistinguishable from an attacker asserting that a payment succeeded.

## Testing against this SPI

`payments-core` ships `RecordingPaymentProvider` in the main artifact — not a test jar, because a
test-scoped classifier is a dependency people fail to add and then reimplement badly.

```java
RecordingPaymentProvider payments = RecordingPaymentProvider.builder()
        .without(ProviderCapability.REFUND)             // prove your code handles a provider that cannot
        .build()
        .willReturnCheckout(RedirectTarget.of("https://pay.example/abc", "chk_abc"))
        .willFailCheckout(new PaymentProviderException(
                "timed out", "recording", null, true, null));   // outcomeUnknown

checkout.begin(order);

assertThat(payments.lastCheckout().merchantReference()).isEqualTo("order-1");
assertThat(payments.scriptExhausted()).isFalse();
```

Scripted answers come back in order; a scripted exception is thrown rather than returned; and an
**unscripted call throws**, naming the method and what to script. A double that quietly returns null is how
a test passes for a reason nobody intended.

Every event and snapshot has a public builder, so a suite can construct any of them.

## Signature verification

**Bankart** — HMAC-SHA512 over five components joined by a single `\n`, Base64 of the raw MAC bytes:

```
POST
sha512hex(request body, exactly as transmitted)
application/json; charset=utf-8
Tue, 21 Jul 2020 13:15:03 GMT
/api/v3/transaction/yourApiKey/debit
```

The `X-Signature` header carries the **bare Base64 value**, with no `Gateway KEY:` prefix. The inner body
hash is hex; the outer HMAC is binary → Base64 and *not* hex, which is the step both vendors' docs
specifically warn about. Outbound, the client sends `Date` and `X-Date` with the same value, because the
docs give `X-Date` precedence and something in transit may rewrite `Date`. Inbound, the verifier recomputes
in constant time and rejects a `Date` more than 60 seconds from now.

If a signature is being rejected, `HmacSigner.canonicalMessage(...)` returns the exact string being hashed.
Compare that, not the signature.

**AgentaOS** — header `x-agentaos-signature`, value `t=<unix seconds>,v1=<hex>`. HMAC-SHA256, hex-encoded,
over the string `<timestamp>.<raw body>`. The default tolerance is 300 seconds, and a timestamp in the
*future* is rejected as well as a stale one.

## Open questions — Bankart

Where the published documentation is silent or contradicts itself, this library takes the defensive reading
and records why. Each of these is settled by a live sandbox and by nothing else; until then the list is the
honest statement of what is assumed.

**1. Notifications are JSON, not XML.** The integration guide says the gateway "sends a notification XML to
the callback URL" and points at the API reference for details — where every notification example is JSON.
The same guide writes `referenceTransactionId (XML) / referenceUuid (JSON)`, which suggests the XML wording
survives from the older API. This library parses JSON.

**2. The documented signature example does not reproduce as printed.** Both confirmed by reproducing the
gateway's own published signature independently, both pinned in `HmacSignerTest`:

- The body that is hashed is the **compact** JSON actually transmitted. The docs display their example body
  pretty-printed, but the SHA-512 they publish is of the compact form.
- The signed URI carries the **substituted** API key. The docs' concatenation block prints
  `/api/v3/transaction/{apiKey}/debit`, yet their published signature only reproduces from
  `/api/v3/transaction/my-api-key/debit`.

**3. The `X-Signature` format was a real discrepancy, and it is settled.** Some pages describe the value as
`Gateway API_KEY:BASE64_SIGNATURE`; the v3 pages describe a bare signature. The OpenAPI specification does
not mention signatures at all — its only security scheme is Basic Auth — so it cannot arbitrate. The prose
documentation's worked example uses the bare form and *reproduces*, so that is what this library sends and
verifies, pinned by a test naming the source.

**4. The notification's own signature mechanism is under-specified.** The docs say notifications are signed
"the same as described in Signature" with values taken from the request received, but do not state which
header carries it (`X-Signature` is assumed by symmetry), nor which URI form is signed — the callback path
alone, or path plus the query string a `callbackUrl` may carry. Pass the URI you believe was signed; start
with path-and-query, since that is what you registered. *"When no signature is sent with the request, the
payload will be hashed using MD5"* appears to describe the unsigned case; **a missing signature is never
accepted**, and `BodyDigest.MD5` exists only for a deployment observed to sign that way.

**5. GET signing is not documented.** The docs describe signing only for the transaction API's POSTs. The
status API's GETs are signed here with the hash of an empty body and a `Content-Type` sent alongside, so
both sides have the same string. If your connector rejects signed GETs, use a config without a shared secret
for status lookups.

**6. `Content-Type` case.** The docs' example signs `application/json; charset=utf-8` in lower case, which
is what this client sends and signs. A raw-socket probe confirms `java.net.http` transmits it byte-for-byte
as set.

**7. Inconsistent field shapes across the docs' own examples**, all handled:

| Field | Variation |
|---|---|
| `returnData` | Flat with `_TYPE` in notifications; nested under `creditcardData` in the status API |
| `transactionType` | `"debit"` in one status example, `"DEBIT"` elsewhere — matched case-insensitively |
| `code` (notification) | Typed as a number, sent as the string `"2016"` in the example |
| `amount` | A JSON string in most examples, a JSON number in the chargeback example |
| Status errors | Named `message`/`code`, not `errorMessage`/`errorCode` as in transaction errors |
| `ScheduleStatus` | The `start` endpoint's own example sends `NON-EXISTING`, a value the enum does not declare |
| `ScheduleResponse` | Declares `additionalProperties: false`, yet every error example carries `errorMessage`/`errorCode`, which it does not declare |
| `OptionsResponse.options` | Declared as an array of `{key,value}`; the endpoint's own example returns a map |
| `PayByLinkData` | The OpenAPI schema says `{payByLink, sendViaEmail}`; the prose docs' table and example say `{expiresAt, cancelUrl}`. The prose version is modelled — the schema looks stale |

**8. Bankart has no subscription webhook of its own.** `TransactionType` has no `SCHEDULE` value: a
recurring charge arrives as an ordinary transaction callback carrying a populated `scheduleData`, and there
is no discriminator separating "schedule paused" from "schedule cancelled" from "renewal failed". The
adapter maps what the payload allows and leaves the rest to `reconcile`, which reads the schedule directly.

**9. A pay-by-link cancel endpoint exists but is undocumented.** The prose docs show `cancelUrl` values
pointing at `/api/v3/payByLink/{id}/cancel`, a route absent from the OpenAPI specification's paths
entirely. Its verb and request shape are unknown, so nothing here calls it.

## AgentaOS is provisional, and says so

There is no AgentaOS API reference. No page exists at `agentaos.ai/docs`, `/api`, `docs.agentaos.ai` or
`developers.agentaos.ai`; the open-source TypeScript SDK `@agentaos/pay` is the entire specification. Reading
a client's source tells you what that client **sends**, not what the server **accepts** — usually the same
thing, occasionally not.

So anything in `payments-agentaos` not read verbatim in that source is annotated **`@Provisional`**, with a
javadoc sentence naming the evidence it rests on. The annotation is retained at runtime and a test
enumerates the inventory, so one added without stating its evidence fails the build.

The distinction matters because it changes what a failure means. A signature mismatch in a verified adapter
is a bug here or an attack; the same mismatch in a provisional one may simply be a guess that was wrong, and
the first thing to check is the assumption rather than the code.

**One provisional decision reaches your handler directly: the event id.** AgentaOS payloads carry no event
id, so `payments-agentaos` synthesises one — `"agentaos_"` plus the first 32 hex characters of
`SHA-256(type + "\n" + resourceId + "\n" + signatureTimestamp)`. It is stable across a redelivery **if**
AgentaOS re-sends the timestamp it first signed with, which nothing in the SDK states either way. If you
find duplicates surviving your dedupe, that assumption is the first thing to check — and it is worth a
belt-and-braces uniqueness constraint on the resource plus event type until a real account settles it.
`occurredAt` is that same signature timestamp, for the same reason: it is the only time the payload carries.

`SPECS.md` carries the full provisional inventory and the list of things only a real account can settle —
among them whether test mode is a separate backend or a flag on the same one, the true event catalogue and
its retry policy, the numeric rate limit, and whether a refund API exists at all. **It does not in the SDK,
so this adapter does not declare `REFUND` and refuses rather than inventing an endpoint.**

## Building

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw clean verify
```

Requires JDK 25. Each module gates its own JaCoCo coverage; no test reaches a live gateway.

## Documentation

- Bankart [API Reference V3](https://gateway.bankart.si/documentation/apiv3) and
  [integration guide](https://gateway.bankart.si/documentation/gateway)
- Bankart sandbox [OpenAPI specification](https://bankart.paymentsandbox.cloud/Schema/V3/OpenApiSpecification.yml)
- AgentaOS [`@agentaos/pay` source](https://github.com/AgentaOS/agentaos/tree/main/packages/pay) — the spec

## License

MIT — see [LICENSE](LICENSE). Copyright (c) 2026 Aethereal Tech.
