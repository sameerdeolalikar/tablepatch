# TablePatch

**Review a table change. Apply it only to the table you actually reviewed.**

TablePatch is a visual CSV comparison workspace with a Java backend and a companion
CLI for applying reviewable, versioned patches. It targets small catalogue and inventory workflows:
inspect added, removed, and changed records without losing quoted fields, leading
zeros, or row order. The bundled examples use fictional stationery inventory.

This is a new project with an initial in-memory implementation and no external
Java dependencies. The optional UI communicates only with its local Java server; it does not upload
tables to external services.

## Open the visual workspace

```bash
sh build.sh test
java -jar build/tablepatch.jar serve
```

Open **http://127.0.0.1:7411**. Start with “Try sample inventory,” or import/paste
original and incoming CSV files. Choose a unique key, review highlighted fields,
search/filter records, and select the original or incoming version for each row.
Download a resolved CSV or a patch that can be applied by the existing CLI.
The server validates each export against the same comparison engine.

The UI uses 50-row pages and supports up to 2,000 rows per table, 1 MB file-picker
uploads, and a 4 MB encoded request. Decisions remain in memory until the page is
closed; changing an input invalidates the comparison. It supports row-level choices,
not arbitrary per-cell edits or three-way merging. Export order follows incoming rows,
then retained original-only rows. Browser downloads follow browser overwrite behavior;
the CLI's no-overwrite guarantee applies to CLI file writes.

## Build and try

Requires JDK 21+ and a POSIX shell for the convenience script:

```bash
git clone https://github.com/sameerdeolalikar/tablepatch.git
cd tablepatch
sh build.sh test
java -jar build/tablepatch.jar compare examples/before.csv examples/after.csv \
  --key sku --output changes.csv
java -jar build/tablepatch.jar apply examples/before.csv changes.csv \
  --output updated.csv
```

Set `JAVA_HOME` if your JDK is not on PATH. All commands run locally. Existing
output files are never overwritten, including the source. A completed sibling
temporary file is published with a no-clobber hard link; use a local filesystem
with hard-link support. Unsupported filesystems fail instead of falling back to
a potentially partial overwrite. Exit code 2 means an input, conflict, or I/O error.

## CSV rules

- UTF-8, comma separator, optional BOM, LF or CRLF record endings.
- Quoted fields support commas, embedded newlines, and doubled quotes.
- Headers must be unique and nonempty; records must match the header width.
- One named key column is required. Empty and duplicate keys are rejected.
- Values are compared as exact text: `001` differs from `1`, and `1.0` from `1.00`.
- Schemas must match in name and order. Key changes appear as remove/add operations.
- Each source table is limited to 20 MB, 100,000 rows, and 256 columns; patches
  have a separate 64 MB input limit. Streaming is not implemented yet.

## Patch format and conflicts

Patches are ordinary CSV documents with these record types:

```text
tablepatch,1,SOURCE_SHA256,TARGET_SHA256
key,sku
columns,sku,name,quantity
order,P004
order,P001
order,P002
remove,P003,Ruler,1
update,P001,Paper sheets,12,P001,Paper sheets,18
add,P004,Glue stick,2
```

Hashes are computed over the canonical parsed table, not raw line endings.
`order` records preserve the target's row order. Each update stores the complete
before and after record. Applying verifies the source hash, schema, before-images,
and resulting target hash before creating output. A changed source or an incomplete
patch is an error. SHA-256 detects mismatches; it is not a digital signature and
does not establish the author or trustworthiness of a patch.

Three-way merge conflict resolution, numeric equivalence rules, composite keys, and
schema migration remain planned. Never use private or customer data in issue
reports or examples; this project's fixtures are entirely synthetic.

## Development

`sh build.sh test` compiles with all compiler warnings treated as errors and runs
13 dependency-free regression tests. They cover multiline CSV, escaped quotes,
duplicate keys, patch conflicts, target ordering, precision preservation, generated
CSV round trips, and output protection. Initial validation used Temurin 21 on Linux.

See [ROADMAP.md](ROADMAP.md) and [CONTRIBUTING.md](CONTRIBUTING.md). GitHub Actions
is not configured. Licensed under [MIT](LICENSE).

## Browser verification

With the server running, install Node.js 20+ and run:

```bash
npm ci
npx playwright install chromium
npm run test:ui
```

The browser test verifies row choices, filtering, exported CSV/patch equivalence
through the Java CLI, invalid input, request-origin checks, and a 390px layout.
Set `JAVA_HOME` if Java is not on PATH. Node and Playwright are development tools;
the application itself needs no JavaScript package installation. See
[UI.md](UI.md) for architecture and development scenarios.
