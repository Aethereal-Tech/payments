## Why

Every wire fact in `bankart-adapter` is read from documentation and an OpenAPI file; none has been exchanged
with a running gateway.

## What Changes

Behaviour does not change by itself. This proposal is to run `payments-bankart` against a live Bankart sandbox
and settle, by observation, what today is read from documents alone — starting with the README's "Open
questions" section, which is the honest statement of what is currently assumed.

## Capabilities

### New Capabilities

None — `skip_specs: true` until a sandbox run settles something and the spec is updated to match.

### Modified Capabilities

None yet.

## Impact

`payments-bankart`'s README "Open questions" section and `bankart-adapter`'s "OpenAPI/prose disagreements"
requirement are the surfaces a sandbox run would update. The parked `bankart-internal-json-reader` change waits
on this one, deliberately: rewriting a parser for a wire format nobody has yet exchanged with a live gateway is
rewriting a guess.

## Status

**Tag.** Untagged — not done yet.

**Why it waits.** Sandbox credentials are not self-service — the gateway administrators issue them by hand.

**Prerequisites (owner).** The maintainer obtains sandbox credentials from Bankart's gateway administrators or
an integration engineer.
