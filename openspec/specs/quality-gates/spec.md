# Quality gates

## Purpose

How this reactor proves its own claims: WireMock instead of a live gateway, independently-computed signature
vectors, and per-module JaCoCo floors that only ever rise.

## Requirements

### Requirement: No test reaches a live gateway
No test SHALL call `gateway.bankart.si` or `api.agentaos.ai`. Bankart's tests SHALL run against WireMock with
fixtures copied verbatim from the specification's own examples; AgentaOS's tests SHALL run against WireMock plus
signature vectors computed independently of the class under test.

#### Scenario: Bankart tests run entirely against WireMock fixtures
- **WHEN** `payments-bankart`'s test suite runs
- **THEN** every HTTP interaction is against WireMock, using fixtures copied verbatim from the specification's
  own examples

#### Scenario: AgentaOS signature vectors are computed independently
- **WHEN** `payments-agentaos`'s webhook signature tests run
- **THEN** the expected signature is computed independently of `AgentaOsWebhookVerifier`, never by calling the
  class under test

### Requirement: Coverage is gated per module, as a ratchet
Coverage SHALL be gated in each module's own pom, never merged into one exec file across modules. A floor
SHALL only ever rise, never be lowered to pass a build.

At the time of writing: `payments-core`, `payments-bankart` and `payments-agentaos` each hold a floor of 90%
line / 80% branch. `payments-core` measured 99.4% line / 97.2% branch across 206 tests; `payments-bankart`
measured 96.7% line / 93.2% branch across 201 tests; `payments-agentaos` measured 99.0% line / 93.7% branch
across 221 tests. `payments-core`'s pom comment SHALL say what the number covers, because "a module of records"
is exactly the shape where a percentage can be met by touching nothing that matters.

#### Scenario: Each module's coverage is measured against its own floor
- **WHEN** the reactor is built
- **THEN** `payments-core`, `payments-bankart` and `payments-agentaos` are each measured against their own
  module's JaCoCo floor, not a reactor-wide number

#### Scenario: A floor is never lowered to pass a build
- **WHEN** a module's measured coverage would fail its current floor
- **THEN** the floor is not lowered to make the build pass; the missing coverage is added instead

### Requirement: JaCoCo includes patterns use the class file path separator
JaCoCo `includes` patterns SHALL use ANT-style patterns over the **class file path**, with `/` as the separator,
never `.`. A dotted pattern matches nothing, and an includes filter matching nothing makes the check pass
vacuously, reporting success at any real coverage.

#### Scenario: A dotted includes pattern is never used
- **WHEN** a module's pom declares a JaCoCo `includes` pattern
- **THEN** it uses `/` as the package separator over the class file path, never `.`
