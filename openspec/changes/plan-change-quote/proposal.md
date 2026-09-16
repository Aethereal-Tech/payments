## Why

AgentaOS exposes `GET .../plan-change/preview` then `POST .../plan-change` — a quote-then-commit pair whose
quote carries proration money (`dueTodayMinor`, `nextInvoiceMinor`, a `prorationDate` in Unix seconds that must
be passed back).

## What Changes

A `PlanChangeQuote` operation would be added to the SPI. Today the SPI models the plan-change EVENT
(`SubscriptionPlanChanged`) but no operation, because modelling the quote means modelling proration, which is
the one thing `SubscriptionPlanChanged` deliberately refuses to carry. Bankart has no equivalent and would
refuse the operation if it were added.

## Capabilities

### New Capabilities

None — `skip_specs: true` until a consumer's need is confirmed.

### Modified Capabilities

None yet.

## Impact

`payment-spi`'s event table, and `agentaos-adapter`'s client surface table, would both gain an entry.

## Status

**Tag.** Untagged — not done yet.

**Why it waits.** No consumer has confirmed it needs proration modelled as an operation rather than left to the
provider's own hosted billing pages.

**Prerequisites (owner).** A consumer that needs it asks for it; that consumer owns the design conversation
about what the quote-then-commit shape should look like on the SPI side.
