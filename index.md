---
tags: [index, navigation, ai-context, documentation-map]
summary: "Central navigation map for all project documentation. Read this FIRST to determine which file to load."
---

# MYJOURNAL — Documentation Index

> **Context Boundary Rule:** Only read a specific file if the user's query matches its Semantic Summary below. Do NOT load all files by default — use this index to select the minimum necessary files.

## File Map

### CLAUDE.md
- **Semantic Summary:** Project architecture reference — directory structure, tech stack, database schema, AndroidBridge API, build instructions, CSS theme system, and key coding conventions.
- **Key Keywords:** project structure, build, tech stack, database schema, AndroidBridge, themes, conventions, encryption, WebView, sql.js
- **When to read:** Architecture questions, "where is X?", build issues, coding conventions, schema lookups.

### REQUIREMENT.md
- **Semantic Summary:** Complete functional requirements specification covering all 12 feature areas — authentication, storage, entries, dashboard, views, explorer, reports, settings, weather, Android platform, components, and file downloads.
- **Key Keywords:** requirements, features, authentication, biometric, entries, dashboard, views, explorer, reports, settings, weather, Android, components, downloads, spec
- **When to read:** "How should X work?", feature behavior questions, acceptance criteria, implementation spec.

### TODO.md
- **Semantic Summary:** Version-by-version feature completion history (v0.9–v1.4) and remaining backlog items with checkbox status.
- **Key Keywords:** todo, backlog, completed, version history, changelog, planned features, remaining work
- **When to read:** "What's done?", "What's left?", version history, prioritization, feature status.

### ISSUES.md
- **Semantic Summary:** Known bugs, platform limitations, fixed issues (with dates), and architectural constraints like WebView blob URL crashes and sql.js memory limits.
- **Key Keywords:** bugs, issues, limitations, fixed, WebView crash, blob URL, WASM, encryption HTTPS, known problems
- **When to read:** Bug investigation, "why doesn't X work?", platform constraints, workaround lookups.

### TO_TEST.md
- **Semantic Summary:** Feature-by-feature test checklist with pass/fail status — covers critical path, all UI areas, Android activities, cross-platform, and regression items.
- **Key Keywords:** testing, checklist, QA, verification, regression, test status, manual test, critical path
- **When to read:** "What needs testing?", test planning, verification status, QA coverage gaps.

### COMPONENTS.md
- **Semantic Summary:** API documentation for reusable UI components (ResultGrid, RankedPanel, RecordViewer, CollapsiblePanel) — constructor options, methods, CSS classes, and usage locations. Also lists planned components.
- **Key Keywords:** components, ResultGrid, RankedPanel, RecordViewer, CollapsiblePanel, Bootstrap, API, options, CSS classes, reusable UI, HD icon buttons, image buttons
- **When to read:** Using or modifying shared components, Bootstrap store API, adding new UI, checking component API, HD icon rendering, planned component designs.

### README.md
- **Semantic Summary:** Minimal public-facing project overview for GitHub display. Content is a subset of CLAUDE.md.
- **Key Keywords:** readme, overview, GitHub
- **When to read:** Rarely — prefer CLAUDE.md for the same information with more detail.

## Overlap Notes

- **README.md ↔ CLAUDE.md:** README is a strict subset. For AI context, always prefer CLAUDE.md.
- **ISSUES.md "Limitations" ↔ ISSUES.md "Known Issues":** Some overlap within the file itself; both describe current constraints.
- **REQUIREMENT.md § "Components" ↔ COMPONENTS.md:** REQUIREMENT.md has a brief component summary; COMPONENTS.md has the full API. Use COMPONENTS.md for implementation details.
- **TODO.md completed items ↔ REQUIREMENT.md:** TODO tracks *what was built when*; REQUIREMENT tracks *how it should work*. Different purposes despite topic overlap.

## Quick Lookup

| Query type | Read first | Then if needed |
|------------|-----------|---------------|
| "How to build?" | CLAUDE.md | — |
| "How does feature X work?" | REQUIREMENT.md | COMPONENTS.md |
| "What's the DB schema?" | CLAUDE.md | REQUIREMENT.md §2 |
| "Is feature X done?" | TODO.md | — |
| "Why is X broken?" | ISSUES.md | TO_TEST.md |
| "What needs testing?" | TO_TEST.md | — |
| "How do I use ResultGrid?" | COMPONENTS.md | — |
| "What's the project structure?" | CLAUDE.md | — |
