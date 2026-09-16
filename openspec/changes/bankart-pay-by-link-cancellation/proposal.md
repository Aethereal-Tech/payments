## Why

The prose docs show a `cancelUrl` pointing at `/api/v3/payByLink/{id}/cancel`, a route absent from the OpenAPI
specification's paths entirely.

## What Changes

`BankartClient` would gain a method calling that route, once its verb and request shape are known.

## Capabilities

### New Capabilities

None — `skip_specs: true` until the route can be observed rather than inferred.

### Modified Capabilities

None yet.

## Impact

`bankart-adapter`'s "OpenAPI/prose disagreements" requirement already records this route's absence from the
OpenAPI spec's paths.

## Status

**Tag.** PARKED.

**Why it waits.** Its verb and request shape are unknown — inferred only from a field inside an example
`cancelUrl` value, never from a path definition in either source.

**Prerequisites (owner).** Observing the route's real verb and shape, which needs sandbox access (see
`bankart-sandbox-verification`).
