# Reactor shape

## Purpose

Reactor shape covers the three published artifacts, their dependency posture, why that posture is split by
DEPENDENCY rather than by tidiness, how the posture is kept checkable, and the coordinates carried over from
the single-artifact `bankart-gateway` release.

## Requirements

### Requirement: Three artifacts publish from one commit at one version
`net.aetherealtech:payments-core`, `payments-bankart` and `payments-agentaos` SHALL publish from one commit at one
version. JDK 25, with `maven.compiler.release=25` so the bytecode version and the API surface cannot disagree.

Splitting them into separate release trains would let a consumer resolve `payments-core:0.3.0` against
`payments-bankart:0.1.0` and meet the mismatch as a `NoSuchMethodError` at the first webhook.

#### Scenario: All three modules release together
- **WHEN** a release is cut
- **THEN** `payments-core`, `payments-bankart` and `payments-agentaos` are published from the same commit at the
  same version

### Requirement: payments-core carries the provider-neutral SPI at zero runtime dependencies
`net.aetherealtech:payments-core` SHALL be the provider-neutral SPI, the model, the events, the refusals and
`RecordingPaymentProvider`, with **zero runtime dependencies** — the artifact's promise rather than a nice-to-have.

#### Scenario: payments-core resolves nothing under it
- **WHEN** `dependency:tree` is run against `payments-core`
- **THEN** it shows nothing under it

### Requirement: payments-bankart depends on Jackson and nothing else
`net.aetherealtech:payments-bankart` SHALL be the Bankart (IXOPAY) Transaction API v3 client and its
`PaymentProvider`, depending on `com.fasterxml.jackson.core:jackson-databind` and nothing else beyond
`payments-core`.

#### Scenario: payments-bankart resolves payments-core plus Jackson only
- **WHEN** `dependency:tree` is run against `payments-bankart`
- **THEN** it shows `payments-core` plus `jackson-databind` and its own two transitives, and nothing more

### Requirement: payments-agentaos carries an internal JSON reader instead of a library dependency
`net.aetherealtech:payments-agentaos` SHALL be the AgentaOS gateway client and its `PaymentProvider`, with **zero
runtime dependencies**, using an internal JSON reader instead of a library.

The split is about the DEPENDENCY, not about tidiness. A consumer that only talks to AgentaOS must not receive
Jackson 2 from this reactor — a known consumer runs Jackson 3, where an unwanted Jackson 2 is not a version
conflict Maven can mediate but a second, differently-named library nobody asked for. Adding a runtime dependency
to any module needs a reason in the PR description; adding one to `payments-core` or `payments-agentaos` needs a
better one, because it costs those modules the only thing they promise.

#### Scenario: payments-agentaos resolves payments-core and nothing else
- **WHEN** `dependency:tree` is run against `payments-agentaos`
- **THEN** it shows `payments-core` and nothing else

#### Scenario: A consumer of only payments-agentaos never receives Jackson
- **WHEN** a consumer depends on `payments-agentaos` alone
- **THEN** no Jackson artifact of any generation is pulled onto its classpath

### Requirement: The root pom imports the Jackson BOM rather than pinning jackson-databind alone
The root pom SHALL import the **`jackson-bom`** in `dependencyManagement`, rather than pinning
`jackson-databind` alone, so the posture constrains versions without putting anything on a classpath and the
two zero-dependency modules stay at zero.

Pinning the one artifact while WireMock brought its own `jackson-core` and `jackson-annotations` produced
databind 2.22.2 against annotations 2.20, and the first stub failed with `NoClassDefFoundError:
com/fasterxml/jackson/annotation/JsonSerializeAs` — a class that exists in 2.22 and not in 2.20. Nearest-wins
mediation cannot see that the three are one library.

#### Scenario: A jackson-bom import prevents the annotations/databind version split
- **WHEN** WireMock brings its own `jackson-core` and `jackson-annotations` onto the test classpath
- **THEN** the root pom's `jackson-bom` import in `dependencyManagement` keeps `jackson-databind` and
  `jackson-annotations` on matching versions
- **AND** `payments-core` and `payments-agentaos` still resolve with nothing added to their own classpath

### Requirement: bankart-gateway 0.1.0 stays published as a separate, single-artifact release
`net.aetherealtech:bankart-gateway:0.1.0` SHALL stay published under that name as the last release before the
reactor, and it SHALL receive no `0.2.0`. The reactor's first release SHALL be `v0.2.0` at the three coordinates
above.

#### Scenario: bankart-gateway 0.1.0 remains resolvable under its original coordinates
- **WHEN** a consumer resolves `net.aetherealtech:bankart-gateway:0.1.0`
- **THEN** it resolves to the last single-artifact release, unaffected by the reactor split

#### Scenario: The reactor's first release is v0.2.0
- **WHEN** the three-artifact reactor is released for the first time
- **THEN** it is released as `v0.2.0` at `payments-core`, `payments-bankart` and `payments-agentaos`
