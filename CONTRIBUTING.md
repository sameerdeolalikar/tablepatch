# Contributing

Use JDK 21+, four-space indentation, and descriptive Java names. Run
`sh build.sh test`; compiler warnings are errors. The test harness uses plain Java
so no build-service or dependency downloads are necessary.

Keep CSV parsing, table identity, patch semantics, and CLI I/O separate. New patch
behavior must preserve unchanged data and reject conflicts before writing output.
Include minimal synthetic before/after files for a bug. Do not add real personal,
customer, employer, or regulated records to tests or issues.

Use focused commits with imperative subjects. Pull requests should explain the
problem, behavior change, compatibility impact, and validation. File-format changes
need versioning and migration notes; do not silently reinterpret existing patches.
