# bankart-gateway

A small, dependency-light Java client for the [Bankart](https://www.bankart.si/) Payment Gateway
Transaction API v3 — hosted checkout, server-to-server operations, card-on-file and recurring
charges, and the asynchronous status notifications that actually decide whether a payment happened.

> **Not affiliated with Bankart or NLB.** This is an independent, unofficial client written by
> Aethereal Tech against Bankart's public documentation. It is not endorsed, supported, certified or
> reviewed by Bankart d.o.o., NLB Group, or IXOPAY. "Bankart" is used only to name the gateway this
> client speaks to. For contractual or certification questions, talk to your Bankart integration
> engineer, not to this repository.

## What it is

- **One runtime dependency**: Jackson. HTTP is `java.net.http.HttpClient`; notifications are JSON, so
  no XML stack is pulled in.
- **Typed** requests and responses per documented operation, as records.
- **Request signing** (HMAC-SHA512) implemented against the published scheme, including two places
  where the published worked example contradicts itself — see [Open questions](#open-questions).
- **Notification verification** with a constant-time comparison and a clock-skew window.
- Tested against recorded wire shapes with WireMock. It never calls the live gateway.

**Requires JDK 25 or newer.** The library targets Java 25 bytecode.

## Install

Artifacts are published to GitHub Packages.

```xml
<dependency>
    <groupId>net.aetherealtech</groupId>
    <artifactId>bankart-gateway</artifactId>
    <version>0.1.0</version>
</dependency>

<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/Aethereal-Tech/bankart-gateway</url>
    </repository>
</repositories>
```

> **GitHub Packages requires authentication even for public artifacts.** This is a GitHub platform
> limitation, not a choice made here: anonymous Maven downloads from `maven.pkg.github.com` return
> 401 regardless of repository visibility. *Any* valid GitHub token with `read:packages` works — it
> does not need access to this repository, and a personal token of your own is fine.

`~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN_WITH_read:packages</password>
    </server>
  </servers>
</settings>
```

The `<id>` must match the `<repository><id>` above.

## Quickstart: hosted checkout

```java
BankartConfig config = BankartConfig.production(
        "yourApiKey", "yourApiUser", "yourApiPassword", "yourSharedSecret");
BankartClient client = new BankartClient(config);

RedirectResult redirect = client.startCheckout(
        PaymentRequest.builder("order-2026-0001", new BigDecimal("9.99"), "EUR")
                .redirectUrls(
                        "https://shop.example/checkout/success",
                        "https://shop.example/checkout/cancel",
                        "https://shop.example/checkout/error",
                        "https://shop.example/payments/bankart/callback")
                .customer(Customer.builder().firstName("John").lastName("Doe").build())
                .description("Example Product")
                .build());

// Persist this BEFORE redirecting. The notification can arrive before the customer's browser does,
// and it identifies the payment by this uuid.
orders.markAwaitingPayment(orderId, redirect.uuid());

response.sendRedirect(redirect.redirectUrl());
```

`RedirectResult` also carries `purchaseId`, `paymentMethod`, and `redirectType` — the last tells you
whether the payment page is meant to be embedded (`isIframe()`) or navigated to.

`BankartClient` is thread-safe. Build it once and share it.

### The synchronous response is not the outcome

For any flow involving a redirect, the gateway's immediate answer only says the transaction started.
The documentation is explicit: *"For the final result you should only trust the notification, NOT the
back redirection."* A customer who lands on your success URL has not necessarily paid.

## Handling the callback

Your callback endpoint has one hard contract:

**Respond HTTP 200 with the body `OK` — exactly that, no quotes, no JSON wrapper.**

Anything else and the gateway retries on an escalating schedule: immediately, then after 1, 5, 15,
60, 120, 180 and 720 minutes, then once every 24 hours for 7 days.

```java
NotificationVerifier verifier = new NotificationVerifier(config.sharedSecret());

@PostMapping(value = "/payments/bankart/callback", produces = MediaType.TEXT_PLAIN_VALUE)
public ResponseEntity<String> callback(
        @RequestBody byte[] body,
        @RequestHeader("Content-Type") String contentType,
        @RequestHeader("Date") String date,
        @RequestHeader(value = "X-Signature", required = false) String signature,
        HttpServletRequest request) {

    SignedRequest received = new SignedRequest(
            request.getMethod(), body, contentType, date, requestUri(request));

    Notification notification = verifier.verifyAndParse(received, signature);

    // Persist FIRST, acknowledge SECOND. "OK" ends the retry schedule, so acknowledging before the
    // state is durable turns a crash into a payment you will never hear about again.
    orders.apply(notification);

    return ResponseEntity.ok(Notification.ACKNOWLEDGEMENT);
}

private static String requestUri(HttpServletRequest request) {
    String query = request.getQueryString();
    return query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
}
```

A `BankartSignatureException` means the notification did not authenticate. Return a non-200 (or
nothing) and do not touch the order — an unverifiable notification is indistinguishable from an
attacker telling you a payment succeeded.

### Two properties your handler must have

Both are documented behaviours, not edge cases:

1. **Idempotent.** The same notification arrives more than once — that is what the retry schedule
   does when an acknowledgement is slow or lost. Key on `notification.uuid()`.
2. **Able to move backwards and forwards.** *"For some payment methods it is possible that a
   transaction changes its state from failed to successful."* A success after a failure for the same
   uuid is legitimate. Let the newest notification win rather than rejecting it as a duplicate.

`Notification` also covers chargebacks (`chargebackData`), chargeback reversals
(`chargebackReversalData`), Account Updater results and network-token status changes
(`extra("networkTokenStatus")`), and post-reconciliation restatements of amount or currency
(`restatedBy()`, `originalAmount()`).

## Signature verification

Both directions use the same scheme: HMAC-SHA512 over five components joined by a single `\n`,
Base64-encoded.

```
POST
sha512hex(request body, exactly as transmitted)
application/json; charset=utf-8
Tue, 21 Jul 2020 13:15:03 GMT
/api/v3/transaction/yourApiKey/debit
```

Outbound, `BankartClient` does this for you whenever the config carries a shared secret, sending
`X-Signature` plus both `Date` and `X-Date` (same value — the docs give `X-Date` precedence, so
sending both survives anything that rewrites `Date` in transit).

Inbound, `NotificationVerifier` recomputes it from the request you received and compares in constant
time, and rejects a `Date` more than 60 seconds from now.

If you are debugging a rejected signature, `HmacSigner.canonicalMessage(...)` returns the exact
string being hashed. Compare that, not the signature.

## Server-to-server operations

```java
client.preauthorize(request);                                       // reserve
client.capture(CaptureRequest.of(txId, preauthUuid, amount, "EUR")); // complete, or partially
client.voidTransaction(VoidRequest.of(txId, preauthUuid));           // cancel
client.refund(RefundRequest.of(txId, debitUuid, amount, "EUR"));     // reverse, or partially
client.register(RegisterRequest.builder(txId).build());              // store an instrument
client.deregister(DeregisterRequest.of(txId, registerUuid));         // forget it
client.payout(PayoutRequest.toReference(txId, refUuid, amount, "EUR"));
client.statusByUuid(uuid);
client.statusByMerchantTransactionId(merchantTransactionId);
```

**Card-on-file and recurring.** Set `withRegister(true)` on the first transaction, then reference its
`uuid` as `referenceUuid` on later charges, with the `transactionIndicator` that matches the scenario
(`INITIAL` then `RECURRING` for a subscription; `CARDONFILE` or
`CARDONFILE_MERCHANT_INITIATED` for stored-card purchases). The indicator is load-bearing under the
card scheme rules — the wrong one is how a series starts getting declined.

**After a timeout.** `BankartTransportException` means the outcome is *unknown*, not failed: the
gateway may have processed it. Recover with `statusByMerchantTransactionId(...)`, never with a blind
retry of the payment.

### What throws and what does not

| Situation | Result |
|---|---|
| Card declined, `returnType: ERROR` | Returned as `TransactionResponse` — a business outcome |
| Bad signature, invalid request, rate limit | `BankartApiException` with the gateway's `errorCode` |
| `startCheckout` when the gateway didn't redirect | `BankartTransactionException` / `BankartException` |
| Connection failure, timeout | `BankartTransportException` |
| Callback fails to authenticate | `BankartSignatureException` |

Branch on `errorCode`, never on `errorMessage`. The docs state the message text may change or be
extended at any time.

## Open questions

Where the public documentation is silent or self-contradictory, this library takes the defensive
reading and records why here. Corrections from anyone with gateway access are welcome — please open
an issue.

**1. Notifications are JSON, not XML.** The integration guide says the gateway "sends a notification
XML to the callback URL" and points at the API reference for details — where every notification
example is JSON. The same guide elsewhere writes `referenceTransactionId (XML) / referenceUuid
(JSON)`, which suggests the XML wording is left over from the gateway's older API. This library
parses JSON. If a deployment genuinely posts XML, `NotificationParser` will reject it and we would
want to know.

**2. The documented signature example does not reproduce as printed.** Two discrepancies, both
confirmed by reproducing the gateway's own published signature independently:

- The body that is hashed is the **compact** JSON actually transmitted. The docs display their
  example body pretty-printed, but the SHA-512 they publish is of the compact form. Sign the bytes
  you send.
- The signed URI carries the **substituted** API key. The docs' concatenation block prints
  `/api/v3/transaction/{apiKey}/debit`, yet their published signature only reproduces from
  `/api/v3/transaction/my-api-key/debit`.

Both are pinned by tests in `HmacSignerTest`.

**3. The notification's own signature mechanism is under-specified.** The docs say the gateway signs
notifications "the same as described in Signature", with the values taken from the request you
received, but do not state:

- **Which header** carries it. `X-Signature` is assumed by symmetry;
  `NotificationVerifier.SIGNATURE_HEADER` is public so you can read it from elsewhere if you observe
  otherwise, since the verifier takes the value rather than the request.
- **Which URI form** is signed — the callback path alone, or path plus the query string a
  `callbackUrl` may carry. You pass the `requestUri` you believe was signed; start with
  path-and-query, since that is what you registered.
- What *"When no signature is sent with the request, the payload will be hashed using MD5"* means in
  practice. It appears to describe the unsigned case, where there is nothing to verify.
  `NotificationVerifier` never accepts a missing signature. `BodyDigest.MD5` exists only for a
  deployment observed to sign that way.

**4. `Content-Type` case.** The docs' example signs `application/json; charset=utf-8` in lowercase,
which is what this client sends and signs. Some HTTP intermediaries canonicalise the charset token
to `UTF-8`; a raw-socket probe confirms `java.net.http` transmits it byte-for-byte as set.

**5. GET signing is not documented.** The docs describe signing only for the transaction API's POSTs.
The status API's GETs are signed here with the hash of an empty body and a `Content-Type` header
sent alongside, so both sides have the same string. If your connector rejects signed GETs, use a
config without a shared secret for status lookups.

**6. Inconsistent field shapes across the docs' own examples**, all handled:

| Field | Variation |
|---|---|
| `returnData` | Flat with `_TYPE` in notifications; nested under `creditcardData` in the status API |
| `transactionType` | `"debit"` in one status example, `"DEBIT"` elsewhere — matched case-insensitively |
| `code` (notification) | Typed as a number, sent as a string `"2016"` in the example |
| `amount` | A JSON string in most examples, a JSON number in the chargeback example |
| Status errors | Named `message`/`code`, not `errorMessage`/`errorCode` as in transaction errors |

**7. Not yet modelled** (0.1.0): schedules, pay-by-link, Level 2/3 data, incremental authorization,
the options request, and the non-card `returnData` variants (iban, phone, wallet), which read as
`null` rather than as a hollow card. Unknown JSON fields are ignored throughout and unknown enum
values degrade to `UNKNOWN`, as the docs' forward-compatibility rules require, so a gateway upgrade
will not break a running integration.

## Building

```bash
./mvnw clean verify
```

Requires JDK 25. The build gate is JaCoCo at 90% line and 80% branch coverage.

## Documentation

- [API Reference V3](https://gateway.bankart.si/documentation/apiv3)
- [Gateway integration guide](https://gateway.bankart.si/documentation/gateway)

## License

MIT — see [LICENSE](LICENSE). Copyright (c) 2026 Aethereal Tech.
