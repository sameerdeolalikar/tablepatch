# TablePatch roadmap

Implemented: local Java-backed visual comparison, highlighted fields, row-level choices,
search/filtering, pagination, validated CSV/patch exports, and the original CLI.

Remaining work estimates describe development still to do, not completed or paid hours.

| Work | Acceptance criteria | Estimate |
| --- | --- | --- |
| Field-level resolution and undo | Choose individual cells; undo/redo decisions; preserve state across filters; keyboard tests | 8–12 hours |
| Column mapping and composite keys | Explicit mapping UI, collision-free multi-column keys, duplicate diagnostics, format compatibility | 8–12 hours |
| Large dataset workflow | Background processing, bounded memory, virtualized grid, cancellation and cleanup tests | 12–18 hours |
| Saved review sessions | Versioned local session import/export, stale-source detection, recovery after reload | 6–9 hours |
| Schema and conflict inspection | Explicit column changes and three-way conflicts with reviewable resolution | 8–12 hours |
| Accessibility and distribution | Screen-reader/keyboard audit, cross-browser checks, responsive refinements, launch packaging | 6–9 hours |

**Estimated remaining work: 48–72 hours.** Use fictional inventory/catalogue data.
The product is a dataset maintenance tool. Assistant comparisons can take place while
implementing normal features; no model benchmarking infrastructure is part of it.
