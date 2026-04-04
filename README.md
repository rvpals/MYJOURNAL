---
tags: [readme, overview, GitHub]
related_files: [CLAUDE.md]
summary: "Minimal public-facing project overview for GitHub. Prefer CLAUDE.md for detailed architecture."
---

# MYJOURNAL - Android Journal App

Personal journal and activity log Android app with encrypted local storage.

## Overview

A WebView-based Android app for keeping a personal journal with rich text editing, image attachments, weather tracking, and more. All data is stored locally on-device in an encrypted SQLite database.

## Features

- **Encrypted storage** — AES-256-GCM encrypted SQLite database (sql.js WASM)
- **Rich text editing** — Quill.js editor with image support
- **Multiple journals** — Each with its own password
- **Biometric login** — Fingerprint/face unlock via AndroidX BiometricPrompt
- **Image attachments** — Camera capture, gallery picker, lightbox viewer
- **Weather tracking** — Open-Meteo API integration, GPS-based
- **Dashboard** — Stats, pinned entries, search, top tags/categories/places/people
- **Custom views** — Saved filter/sort/group combinations
- **SQL Explorer** — Query builder and raw SQL for advanced searches
- **Reports** — HTML, PDF, CSV, and custom template reports
- **12 themes** — Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender, Frost, Navy, Sunflower, Meadow
- **Export/Import** — Encrypted or plain SQLite, CSV, metadata JSON

## Building

Requires JDK 17 and Android SDK (platform 34, build-tools).

```bash
set ANDROID_SDK_ROOT=C:\path\to\Android\Sdk
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

- **Android** — Java, WebView, AndroidX Biometric
- **Web layer** — Plain HTML/CSS/JS (no frameworks)
- **Storage** — sql.js (SQLite compiled to WASM), AES-256-GCM encryption
- **Editor** — Quill.js 1.3.7
- **PDF** — jsPDF 2.5.1
