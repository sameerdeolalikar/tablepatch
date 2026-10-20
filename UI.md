# Visual workspace

The Java `Web` adapter serves bundled HTML/CSS/JavaScript and an in-memory comparison
endpoint. It calls the same `Table` and `Patch` classes as the CLI. No database or
frontend build step is required. The UI caps tables at 2,000 rows and renders 50 at
a time. Source edits invalidate review state; exports are validated by the backend.

The browser keeps choices in memory. It never injects CSV values as HTML. The server
binds to `127.0.0.1`, checks Host/Origin and a custom request header, and sets a restrictive
content policy. This is a local single-user application, not an internet-hosted service.

## Next development scenarios

- Add individual-cell choices with undo/redo. Verify keyboard operation, choice persistence
  across filters, and exact exported values.
- Add column mapping with explicit duplicate-key feedback. Verify readable validation,
  consistent mapping previews, and rejection of ambiguous identities.
- Support large tables with background work and virtualization. Measure memory and
  responsiveness; test cancellation and temporary-file cleanup.

These ordinary product tasks make visual clarity, interaction quality, accessibility,
frontend/backend integration, and regression prevention observable when using different
coding assistants. The application does not evaluate or benchmark models itself.

## Verification limits

The repository includes a Chromium workflow test with a 390px viewport check. It is
not a complete screen-reader, keyboard, cross-browser, or performance audit. Full UI
accessibility validation and larger-dataset workflows remain roadmap items.
