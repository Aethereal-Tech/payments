## Why

A `mapper` package, or a `PaymentEventMapper` per adapter, was considered and decided against.

## What Changes

None. Each adapter's provider class owns its own translation from wire shape to `PaymentEvent`. A shared
mapping layer would need to know every provider's shape, which is the coupling `payment-spi` exists to remove.

## Capabilities

### New Capabilities

None — `skip_specs: true`, decided against.

### Modified Capabilities

None.

## Impact

None; the status quo (translation owned by each adapter's provider class) stays.

## Status

**Tag.** CUT.

**Why it waits.** It does not — decided against.

**Prerequisites (owner).** None; do not re-propose.
