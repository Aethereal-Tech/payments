## Why

AgentaOS's only subscription read IS a list (there is no single-subscription retrieve in the SDK), so a
`listSubscriptions` SPI operation would cost nothing there. Bankart would have to refuse it, since the schedule
API reads one schedule at a time.

## What Changes

A `listSubscriptions` operation could be added to `PaymentProvider`, backed directly by AgentaOS's
`GET /api/v1/gateway/subscriptions?limit=&offset=` and refused by `BankartPaymentProvider` as
`UnsupportedCapabilityException`.

## Capabilities

### New Capabilities

None — `skip_specs: true` until a consumer confirms it needs to sweep rather than look one up.

### Modified Capabilities

None yet.

## Impact

`payment-spi`'s operation table would gain an entry; `agentaos-adapter`'s client surface table already shows
the underlying call.

## Status

**Tag.** PARKED.

**Why it waits.** No consumer has asked to sweep subscriptions rather than look one up by reference.

**Prerequisites (owner).** A consumer's own review asking for it.
