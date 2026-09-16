## Why

Retrying a POST inside the client was considered and decided against.

## What Changes

None. The AgentaOS SDK retries 5xx and network failures automatically; `payments-agentaos`'s adapter does not.
A library that silently retries a payment POST is a library that double-charges, and
`PaymentProviderException.outcomeUnknown()` exists precisely so the caller can recover by asking rather than by
repeating.

## Capabilities

### New Capabilities

None — `skip_specs: true`, decided against.

### Modified Capabilities

None.

## Impact

None; `agentaos-adapter`'s "no retry loop" requirement stays as recorded.

## Status

**Tag.** CUT.

**Why it waits.** It does not — decided against.

**Prerequisites (owner).** None; do not re-propose.
