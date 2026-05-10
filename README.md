---
tags: [readme, overview, GitHub]
related_files: [CLAUDE.md]
summary: "Public-facing project overview for GitHub. Prefer CLAUDE.md for detailed architecture."
---

# MYJOURNAL - Android Journal App

Personal journal and activity log Android app with encrypted local storage.

## Overview

A fully native Kotlin Android app for keeping a personal journal with rich text editing, image attachments, weather tracking, and more. All 15 screens are native Kotlin activities. All data is stored locally on-device in an AES-256-GCM encrypted SQLCipher database. Version 2.5.1.

## Features

- **Encrypted storage** — AES-256-GCM encrypted SQLCipher database
- **Multiple journals** — Each with its own password
- **Biometric login** — Fingerprint/face unlock via AndroidX BiometricPrompt
- **Image attachments** — Camera capture, gallery picker, base64 storage with thumbnails
- **File attachments** — Attach any files to entries as zip archives, configurable storage path, view/download/delete from entry viewer; 📎 icon in entry lists links directly to attachment screen
- **Weather tracking** — Open-Meteo API integration, GPS-based, optional auto-populate on new entry
- **Draft entries** — Save entries as drafts for future dates, one-click publish when ready
- **Dashboard** — Stats (collapsible half-size cards), pinned entries, search, ranked panels (collapsible with expand), configurable widgets, template shortcuts, drafts panel, Today in History, Daily Inspiration, modern 3D card components and collapsible headers, quick-remove ✕ buttons on panels
- **What's New** — In-app changelog accessible from About screen
- **App font customization** — Font family (9 options) and size scale across all screens
- **Theme management** — Remove unwanted themes permanently, restore all with one click
- **Custom views** — Saved filter/sort/group combinations, usable as filter presets in Entry List and Reports
- **Pre-fill templates** — Auto-populate new entries with saved field defaults (date, time, title, content, tags, categories); optional dashboard shortcuts for one-tap entry creation
- **SQL Explorer** — Query builder, raw SQL, CSV and iCalendar export, SQL Library (save/load/edit/delete queries)
- **Reports** — HTML (exports to browser), PDF (native PdfDocument), CSV
- **Calendar view** — Monthly grid with entry dots, day selection
- **Entry locking** — Prevent accidental edits
- **Complete Backup/Restore** — Full backup (database + attachments + settings) to zip with timestamp, restore from backup folder with overwrite confirmation
- **Backup/Restore** — SAF folder-based backup on Android
- **20 themes** — Light, Dark, Ocean, Midnight, Forest, Amethyst, Aurora, Lavender, Frost, Navy, Sunflower, Meadow, Rose, Copper, Slate, Ember, Sage, Dusk, Mocha, Arctic (removable)
- **Export/Import** — CSV, metadata JSON, encrypted DB

## Building

Requires JDK 17 and Android SDK (platform 34, build-tools).

```bash
set JAVA_HOME=E:\Prog\Java\jdk-17
set ANDROID_SDK_ROOT=C:\Android\android-sdk
gradlew.bat assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Tech Stack

- **Android** — Kotlin, SDK 34, fully native activities (no WebView)
- **Database** — SQLCipher 4.5.6 (encrypted SQLite)
- **Encryption** — AES-256-GCM, PBKDF2-SHA256 (100k iterations) via javax.crypto
- **PDF** — Native Android PdfDocument API
- **Weather** — Open-Meteo API (free, no key required)
- **Geocoding** — Photon / Nominatim / Google (configurable)
