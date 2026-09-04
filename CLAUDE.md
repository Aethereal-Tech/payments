# Conventions for this repository

A standalone, private library reactor. These are its own rules; nothing here inherits from another repo.

**`SPECS.md` is the record of what exists** — the artifacts, the SPI, each adapter's wire facts and status
mappings, the consumers, and the planned work with its reasons; this file is the rules alone.

## The shape

Three artifacts, one repository, one version, one release:

- **`payments-core`** — the provider-neutral SPI, the events, the refusals, the recording test double.
  **Zero runtime dependencies**, and that is the artifact's promise rather than a nice-to-have.
- **`payments-bankart`** — the Bankart (IXOPAY) Transaction API v3 client, and its `PaymentProvider`.
  **Jackson, and nothing else.**
- **`payments-agentaos`** — the AgentaOS client, and its `PaymentProvider`. **Zero runtime dependencies**,
  which is why it carries an internal JSON reader instead of a library.

**The split is about the DEPENDENCY, not about tidiness.** A consumer that only talks to AgentaOS must not
receive Jackson 2 from us — a known consumer runs Jackson 3, where an unwanted Jackson 2 is not a version
conflict Maven can mediate but a second, differently-named library nobody asked for. Adding a runtime
dependency to any module needs a reason in the PR description; adding one to `payments-core` or
`payments-agentaos` needs a better one, because it costs those modules the only thing they promise.

**All three publish from one commit at one number.** Splitting them into separate release trains would let a
consumer resolve `payments-core:0.3.0` against `payments-bankart:0.1.0` and meet the mismatch as a
`NoSuchMethodError` at the first webhook.

## The docs are the spec

Every field name, path, enum value and header comes from the provider's own published material. Do not
invent fields, guess at shapes, or copy them from another integration. If something is needed and not
documented, read the source again first; if it genuinely is not there, model it defensively, say so in a
code comment, and record it — in the README's "Open questions" for Bankart, in `SPECS.md`'s provisional
inventory for AgentaOS. Those sections are part of the deliverable: they are how a reader tells what we know
from what we assumed.

Where a source contradicts itself, the reproducible behaviour wins over the prose, and the discrepancy gets
a test that pins it plus a note saying which way we went and why. `HmacSignerTest` is the worked example.

**What counts as the spec differs per module, and this matters:**

- **Bankart** — the sandbox OpenAPI specification (`bankart.paymentsandbox.cloud/Schema/V3/…`) first, the
  prose documentation second. Where they disagree, say which one a decision followed; the spec is silent on
  signing and rate limits, and stale on `PayByLinkData`, so "the spec" alone is not an answer.
- **AgentaOS** — there is **no API reference at all**. The open-source TypeScript SDK's SOURCE
  (`github.com/AgentaOS/agentaos`, `packages/pay/src`) is the spec, and reading a client tells you what that
  client SENDS, not what the server ACCEPTS. Anything not read verbatim there is
  **`@Provisional`**, with a javadoc sentence naming the evidence — and the annotation's inventory is
  asserted by a test, so one added without thinking fails the build.

**Never call a live gateway**, from a test or otherwise. `gateway.bankart.si` and `api.agentaos.ai` are
production payment systems: a payment API has no consequence-free test path, and a suite that needs
credentials is a suite that quietly stops running.

## Testing

**WireMock, never a live host.** Fixtures under each module's `src/test/resources/fixtures` are copied
verbatim from the source's own examples; keep them that way, including their inconsistencies, because those
inconsistencies are what the parsing has to survive.

**Assert the wire, not the round trip.** A test that serialises and deserialises with the same code proves
nothing about what the provider will accept; assert the actual JSON, the paths, the headers, and the
recomputed signature. Signature vectors are computed independently — never by calling the class under test.

**Coverage is per module**, gated in each module's own pom, and it is a ratchet: raise a floor as coverage
improves, never lower one to pass a build.

- `payments-bankart`, `payments-agentaos`: **90% line, 80% branch**.
- `payments-core`: the same floors over the logic that exists — the refusals, the defensive copies, the
  builders, and the recording double. Its pom comment says what the number covers, because "a module of
  records" is exactly the shape where a percentage can be met by touching nothing that matters.

JaCoCo `includes` patterns are ANT-style over the **class file path**, so the separator is `/` and never
`.`. A dotted pattern matches nothing, and **an includes filter matching nothing makes the check pass
vacuously** — reporting success at any real coverage.

## Build

JDK 25.

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw clean verify
```

**Read Maven's own exit code.** A pipe reports the pipe's status. Run the whole reactor before proposing a
change: a single-module build resolves its siblings from `~/.m2` and can pass against a stale jar.

## Commits

Conventional commits (`feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `test:`, `chore:`). The release version
is derived from these, so the type is a functional statement rather than a label: `feat` mints a minor,
`fix`/`perf`/`refactor` a patch, `!` or `BREAKING CHANGE` a major, and chore-level commits alone produce no
release at all.

**On 0.x, a coordinate change is a `feat:`, not a `feat!:`.** The version says the surface may still move,
and a major bump from 0 would claim a stability this does not have. State the change in the commit body and
in the README instead.

**No AI attribution.** No `Co-Authored-By` for tools, no "generated with" trailers, nothing of the kind in
commit messages, PR bodies, or code comments.

## Comments

Comments explain what the code cannot: why a defensive branch exists, which documented ambiguity a choice
resolves, what breaks if it changes. Never restate the code. A comment saying "HMAC-SHA512 the message"
above a line that HMAC-SHA512s the message is noise; one saying the docs' published vector only reproduces
with the API key substituted is the reason the line looks odd.

**These documents follow the same rule: `CLAUDE.md` for rules, `SPECS.md` for the record, `README.md` for
the consumer — nothing else.** When a FUTURE item lands, it moves to PRESENT in the same commit that ships
it.

## Versioning and publishing

`0.x` — the API may still change. Publishing is automatic from `master` (see
`.github/workflows/publish.yml`); the poms stay on a `-SNAPSHOT` version and the release number is stamped
in by CI with `versions:set -DprocessAllModules=true`, so there is never a version-bump commit to conflict
over and never a module left naming a parent version that no longer exists.

`v0.1.0` was `net.aetherealtech:bankart-gateway`, a single artifact, and it stays published as the last
release under that name. The reactor's first release is `v0.2.0` at the three coordinates above.

**A merge is not finished until the branch is gone and the issue is closed.** Delete the merged branch,
local and remote, in the same step as the merge, and close every GitHub issue the merge resolved with a
comment naming the pull request or commit. A branch left behind is one somebody branches off next week, and
an issue left open is one somebody works twice. Merged branches only: a parked branch stays until its work
lands.

**This is a PRIVATE repository.** A consuming build needs a token with access to the organization's private
packages — `read:packages` alone is not enough, unlike a public GitHub Packages artifact. Keep the README's
Install section saying so, and keep it free of "public" wording and of any invitation to outside
contributors.
