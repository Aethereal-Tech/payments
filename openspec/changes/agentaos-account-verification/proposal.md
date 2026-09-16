## Why

The whole `agentaos-adapter` is written from a client SDK's source, and a client tells you what it sends rather
than what the server accepts.

## What Changes

Behaviour does not change by itself. This proposal is to run `payments-agentaos` against a real AgentaOS
account and settle, by observation, what only a live account can:

- **Whether a redelivery re-signs with a fresh `t=`.** The synthesised event id is derived from the signature
  timestamp, so a re-signed retry produces a different id and a consumer's dedupe stops working. This is the
  single most consequential unknown here.
- **Whether webhook bodies are camelCase or snake_case.** Both are read today, which is the defensive answer
  and not an answer.
- **Whether `GET /api/v1/gateway/subscriptions/{id}` exists.** The SDK exposes only a list, so `reconcile`
  pages it; a single read would make the operation O(1) instead of O(pages).
- **The `x-agentaos-signature` header name.** It appears in an example app and the package README, never in
  `packages/pay/src` — the verifier takes the signature as a parameter and never reads a header.
- **Whether `metadata` survives from checkout creation to `checkout.session.completed`.** The merchant
  reference rides in it; if it does not round-trip, it arrives null.
- **The `limit` default** — the types file says 10, the README says 20, the wallet CLI says 10.
- **The minor-unit exponent for anything but EUR and USD.** Today an unknown currency is refused on a client
  call and omitted on a webhook rather than scaled by a guessed 100.
- **Whether `x402Url` is returned by a given deployment.** The types declare it; the wallet package's own
  comment says it may not be in the published types yet and casts around it.
- Plus, still open from the first reading: whether test mode is a separate backend or a flag on the same one;
  the true event catalogue and its retry policy; the numeric rate limit (a `RateLimitError` exists, no
  published figure does); whether a refund API exists outside `packages/pay`; and the JSON error-body shape
  beyond the `message`/`error` fields the client reads.

Most of these are `@Provisional` in the code with their evidence named — see `agentaos-adapter`'s provisional
inventory requirement. The ones that are not (test mode, the rate limit, the error-body shape) are absences
rather than assumptions: nothing was modelled on them.

## Capabilities

### New Capabilities

None — `skip_specs: true` until an account run settles something and the spec is updated to match.

### Modified Capabilities

None yet.

## Impact

`agentaos-adapter`'s provisional inventory and event-id requirements are the surfaces an account run would
update.

## Status

**Tag.** Untagged — not done yet.

**Why it waits.** An AgentaOS account.

**Prerequisites (owner).** The maintainer obtains an AgentaOS account.
