---
tags: [index, navigation, ai-context, documentation-map]
summary: "Central navigation map for all project documentation. Read this FIRST to determine which file to load."
---

# MYJOURNAL — Documentation Index

> **Context Boundary Rule:** Only read a specific file if the user's query matches its Semantic Summary below. Do NOT load all files by default — use this index to select the minimum necessary files.

## File Map

### CLAUDE.md
- **Summary:** Project architecture reference — directory structure, tech stack, database schema, ServiceProvider pattern, ThemeManager, build instructions, and key coding conventions.
- **Keywords:** project structure, build, tech stack, database schema, ServiceProvider, ThemeManager, themes, conventions, encryption
- **When to read:** Architecture questions, "where is X?", build issues, coding conventions, schema lookups.

### REQUIREMENT.md
- **Summary:** Complete functional requirements for all feature areas — authentication, storage, entries, dashboard, calendar, views, explorer, reports, settings, weather, Android platform, and file downloads.
- **Keywords:** requirements, features, authentication, biometric, entries, dashboard, views, explorer, reports, settings, weather, Android, downloads, spec
- **When to read:** "How should X work?", feature behavior questions, acceptance criteria, implementation spec.

### TO_DO.md
- **Summary:** Remaining backlog, feature-by-feature test checklist, version-by-version completion history (v0.9–v1.5), and Kotlin migration details.
- **Keywords:** todo, backlog, completed, version history, changelog, testing, checklist, QA, Kotlin migration
- **When to read:** "What's done?", "What's left?", "What needs testing?", version history, feature status.

### ISSUES.md
- **Summary:** Known bugs, platform limitations, fixed issues (with dates), and architectural notes for the fully native Android app.
- **Keywords:** bugs, issues, limitations, fixed, known problems, architecture notes, widgets, themes, entry locking
- **When to read:** Bug investigation, "why doesn't X work?", platform constraints, workaround lookups.

### table_structure.md
- **Summary:** Detailed database schema — all 8 tables with column types, constraints, descriptions, indexes, and relationship diagram.
- **Keywords:** database, schema, tables, columns, entries, images, categories, tags, icons, people, widgets, settings
- **When to read:** Schema lookups, column details, relationship understanding, migration planning.

### README.md
- **Summary:** Public-facing project overview for GitHub display. Content is a subset of CLAUDE.md.
- **Keywords:** readme, overview, GitHub
- **When to read:** Rarely — prefer CLAUDE.md for the same information with more detail.

## Quick Lookup

| Query type | Read first | Then if needed |
|------------|-----------|---------------|
| "How to build?" | CLAUDE.md | — |
| "How does feature X work?" | REQUIREMENT.md | CLAUDE.md |
| "What's the DB schema?" | table_structure.md | CLAUDE.md |
| "Is feature X done?" | TO_DO.md | — |
| "Why is X broken?" | ISSUES.md | TO_DO.md |
| "What needs testing?" | TO_DO.md (Test Checklist) | — |
| "What's the project structure?" | CLAUDE.md | — |
