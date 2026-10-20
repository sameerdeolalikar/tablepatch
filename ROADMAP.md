# TablePatch roadmap

The first release candidate is a working exact-text CSV comparator and patcher.
These are estimated future development hours, not completed work or claimed usage.

| Work | Acceptance criteria | Estimate |
| --- | --- | --- |
| Composite keys | Collision-free multi-column identity, clear duplicate diagnostics, compatible patch format evolution | 6–8 hours |
| Streaming large tables | Bounded-memory external sorting/indexing, temporary-file cleanup, cancellation tests | 10–14 hours |
| Typed comparison rules | Opt-in decimal/date normalization with explicit locale and unchanged original values | 7–10 hours |
| Conflict inspection | Report changed-source conflicts by record/field; produce a reviewable resolution workflow | 8–12 hours |
| Schema changes | Explicit column rename/add/remove plans with validation and reversible examples | 6–9 hours |
| Packaging and interoperability | Cross-platform tests, shell completion, original CSV fixtures, and release documentation | 4–6 hours |

**Estimated remaining work: 41–59 hours.** Development fixtures should use
fictional inventories or non-personal public catalogues. This is an application
for table maintenance, not an AI evaluation project.
