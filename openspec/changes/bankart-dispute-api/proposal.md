## Why

Bankart's dispute API (`/dispute/{apiKey}/{accept|metadata|upload-evidence|submit-evidence}/{uuid}`) exists in
the specification but is not wired into `payments-bankart`.

## What Changes

`BankartClient` would gain methods for accepting a dispute, attaching metadata, and uploading or submitting
evidence against a chargeback.

## Capabilities

### New Capabilities

None — `skip_specs: true` until a consumer actually contests a chargeback.

### Modified Capabilities

None yet.

## Impact

`bankart-adapter`'s client surface table would gain the dispute endpoints.

## Status

**Tag.** PARKED.

**Why it waits.** Today chargebacks are surfaced as `ChargebackOpened` / `ChargebackReversed` events and
nothing acts on them; no consumer has needed to contest one yet.

**Prerequisites (owner).** A consumer that actually contests a chargeback.
