## Why

Replacing Jackson in `payments-bankart` with an internal reader would take the reactor to zero runtime
dependencies across all three artifacts.

## What Changes

`payments-bankart` would carry its own JSON reader instead of depending on `jackson-databind`, mirroring what
`payments-agentaos` already does.

## Capabilities

### New Capabilities

None — `skip_specs: true`; `reactor-shape` already records the target dependency posture and would only need
its "Jackson, and nothing else" language retired once this ships.

### Modified Capabilities

None yet.

## Impact

`reactor-shape`'s requirement on `payments-bankart` depending on Jackson would be superseded.

## Status

**Tag.** Untagged — not done yet.

**Why it waits.** The Bankart wire format being worth it — it is large, deeply nested, polymorphic on `_TYPE`,
and carries fields whose JSON type differs between the docs' own examples (an amount that is a string in one
and a number in another, a code typed as a number and sent as `"2016"`). Hand-writing that reader is a real
cost, and it should follow the `bankart-sandbox-verification` change rather than precede it: rewriting a parser
for a format nobody has yet exchanged with a live gateway is rewriting a guess.

**Prerequisites (owner).** Whoever picks it up after `bankart-sandbox-verification` lands.
