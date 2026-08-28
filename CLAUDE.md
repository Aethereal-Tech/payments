# Conventions for this repository

A public, standalone library. These are its own rules; nothing here inherits from another repo.

## The docs are the spec

Every field name, path, enum value and header in this library comes from Bankart's public
documentation:

- https://gateway.bankart.si/documentation/apiv3
- https://gateway.bankart.si/documentation/gateway

Do not invent fields, guess at shapes, or copy them from another IXOPAY integration. If something is
needed and not documented, read the docs again first; if it genuinely is not there, model it
defensively, say so in the code comment, and add it to the README's "Open questions" section. That
section is part of the deliverable, not an afterthought — it is how a reader tells what we know from
what we assumed.

Where the docs contradict themselves, the reproducible behaviour wins over the prose, and the
discrepancy gets a test that pins it plus a note saying which way we went and why. `HmacSignerTest`
is the worked example.

## Testing

**WireMock, never the live gateway.** No test may reach `gateway.bankart.si`. A payments API has no
consequence-free test path, and a suite that needs credentials is a suite that quietly stops
running. Fixtures under `src/test/resources/fixtures` are copied verbatim from the docs' own
examples; keep them that way, including their inconsistencies, because those inconsistencies are
what the parsing has to survive.

Coverage is gated at 90% line and 80% branch. If a change cannot clear that, the change needs tests,
not a lower gate.

Assert the wire, not the round trip. A test that serialises and deserialises with the same code
proves nothing about what Bankart will accept; assert the actual JSON, headers, and recomputed
signature.

## Commits

Conventional commits (`feat:`, `fix:`, `docs:`, `ci:`, `refactor:`, `test:`, `chore:`). The release
version is derived from these, so the type is a functional statement rather than a label:
`feat` mints a minor, `fix`/`perf`/`refactor` a patch, `!` or `BREAKING CHANGE` a major, and
chore-level commits alone produce no release at all.

**No AI attribution.** No `Co-Authored-By` for tools, no "generated with" trailers, nothing of the
kind in commit messages, PR bodies, or code comments.

## Comments

Comments explain what the code cannot: why a defensive branch exists, which documented ambiguity a
choice resolves, what breaks if it changes. Never restate the code. A comment saying
"HMAC-SHA512 the message" above a line that HMAC-SHA512s the message is noise; one saying the docs'
published vector only reproduces with the API key substituted is the reason the line looks odd.

## Versioning

`0.x` — the API may still change. Publishing is automatic from `master` (see
`.github/workflows/publish.yml`); `pom.xml` stays on a `-SNAPSHOT` version and the release number is
stamped in by CI, so there is never a version-bump commit to conflict over.

## Build

JDK 25, `./mvnw clean verify`. Runtime dependencies stay at Jackson alone — adding a second one
needs a reason in the PR description, since "minimal dependencies" is a feature consumers chose this
library for.
