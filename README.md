# LinkVault

A local-only Android bookmarking app. Not a browser — it captures links shared
from other apps (or opened via a link-handler chooser) and stores them as
organized, searchable bookmarks in user-defined folders. No cloud, no
account, no tracking.

**"LinkVault" and the package name `com.linkvault.app` are placeholders.**
Rename them whenever — see "Renaming the app" below.

## Current status: Phase 0-4 complete, Phase 4.5 core batch

The full loop works end to end: capture a link, it's saved with title/
favicon auto-fetched, and you can organize it with folders, tags, pins,
archiving, and notes, search/sort it, and back everything up.

**IMPORTANT if you already have this app installed with real data:**
this update includes the first Room schema change since Phase 0 (adding
tags). A real, correct migration is included (`data/local/Migrations.kt`,
`MIGRATION_1_2`) — no destructive fallback, nothing should be wiped — but
since this couldn't be run against a real device here, **export a backup
first** (Home → ⋮ → Export backup) before installing this version, as a
safety net.

### What's new in Phase 4.5 (this update)

- **Tags**: add any number of tags to a bookmark (overflow menu → Tags).
  Browse everything by tag from Home's overflow menu → Tags. Deleting a tag
  removes it from every bookmark it was on; deleting a bookmark removes its
  tag associations — neither deletes the other side.
- **Archive vs delete**: overflow menu → Archive hides a bookmark from
  normal views without deleting it. Home → ⋮ → Archived to see and
  unarchive (or permanently delete) archived links.
- **Pinned**: overflow menu → Pin. Pinned bookmarks get their own section
  at the top of Home, in addition to still showing in their normal
  folder/unsorted location — pinning doesn't move anything.
- **Notes**: overflow menu → Add/Edit note. A small note icon shows on any
  bookmark that has one.
- **Search** now also matches notes, not just title/URL.
- **Sort**: a sort icon next to search (Home and inside a folder) — newest
  first, oldest first, or alphabetical.

### Deliberately not in this batch

Quick-save bottom-sheet redesign, multi-select bulk actions, browser-bookmark
HTML import, per-folder custom sort, home screen widget, quick-settings
tile, backup reminders, usage stats, QR/local-network device transfer,
dead-link checking, preview thumbnails, read/unread status, nested
sub-folders. **Encryption (Phase 5) is intentionally not attempted alongside
any of this** — it's the one part of this project where a rushed mistake
means data isn't actually protected, not just "a feature is buggy," so it
gets its own dedicated, unhurried pass rather than being bundled in.

### Batch 2 additions (no new schema — no migration risk added)

- **Duplicate detection**: the capture screen shows "Already saved" under
  the URL field on an exact match. A notice, not a block — you can still
  save a legitimate duplicate.
- **Auto-suggest folder**: if you've saved other links from the same domain
  before, the capture screen pre-selects whichever folder they most often
  ended up in (based on a simple most-common-folder count, not anything
  fancier). Runs once from the originally captured URL, not on every
  keystroke, so the selection doesn't jump around while editing.
- **Custom folder colors**: folder overflow menu → Color, a fixed
  8-swatch palette (`ui/common/FolderColorDialog.kt`). Uses `colorTag`,
  which has existed in the schema since Phase 0 — no migration needed.

## Opening the project

1. Open this folder in a recent Android Studio (Otter or newer — needed for
   AGP 9.x support).
2. Let Gradle sync run.
3. Run on an emulator or device (`minSdk 26`, Android 8.0+).
4. **If updating an existing install rather than a fresh one**, watch the
   first launch carefully — that's when `MIGRATION_1_2` actually runs. If
   anything looks wrong, your Phase 4 export (see above) is the recovery
   path: uninstall, reinstall, Import backup.

This project was assembled outside Android Studio — no Android SDK/Gradle
available here, so nothing in this update has been compile- or
migration-verified against a real device. That's true of every phase
shipped so far, but matters more this time specifically because of the
schema change.

*Versions, current as of August 2026:* AGP 9.2.0, Kotlin 2.3.21, KSP 2.3.10,
Compose BOM 2026.06.00, Room 2.8.4 (schema version 2), kotlinx.serialization
1.11.0, WorkManager 2.11.2, Coil 3.5.0, compileSdk/targetSdk 37.

## Architecture notes for this batch

- **Tags** are a proper many-to-many relationship: `Tag` entity +
  `BookmarkTagCrossRef` join table (`data/local/entity/`), added via
  `MIGRATION_1_2`. The join table uses `CASCADE` delete, unlike the
  `SET_NULL` pattern used for Folder/Bookmark — a cross-reference row has
  no meaning once either side is gone, unlike a folder's *contents*, which
  is what `SET_NULL` exists to protect.
- Tag chips aren't shown inline on bookmark cards yet — tags are fully
  manageable (add/remove/delete) and browsable, just not visually
  decorating the main list rows. Scope call to keep this batch from also
  requiring a refactor of how every screen fetches bookmarks.
- **Sort** (`ui/common/SortOption.kt`) is applied in-memory in the
  ViewModels, combined with search via the same `combine()` pattern already
  used for search filtering — no new DAO queries needed, consistent with
  how the rest of the app already works.
- **Archived/Tags screens** (`ui/archived/`, `ui/tags/`) reuse
  `BookmarkListItem` and the shared dialogs rather than introducing new
  list-row UI. `LinkVaultApp.kt`'s screen switch grew from a boolean
  (folder open or not) to a small `Screen` enum to accommodate the two new
  destinations, still without a navigation library.

See earlier sections of this README (preserved in project history / prior
zips) for the full Phase 0-4 architecture, capture-flow, and visual-design
notes — omitted here for length; nothing about them changed in this batch
except where noted above.

## Schema (as of version 2)

**Folder**: `id`, `name`, `parentFolderId`, `createdAt`, `updatedAt`,
`sortOrder`, `colorTag`

**Bookmark**: `id`, `url`, `title`, `folderId`, `notes`, `createdAt`,
`updatedAt`, `faviconUrl`, `isArchived`, `isPinned`

**Tag** *(new)*: `id`, `name`, `createdAt`

**BookmarkTagCrossRef** *(new, join table)*: `bookmarkId`, `tagId`

`colorTag`, `sortOrder`, `parentFolderId` are still unused by the UI
(nested folders, custom folder colors, per-folder sort remain deferred).

## Renaming the app

Currently `LinkVault` / `com.linkvault.app` throughout. Android Studio's
**Refactor → Rename** on the root package handles the Kotlin side;
`app/build.gradle.kts` (`namespace`, `applicationId`) and
`app/src/main/res/values/strings.xml` (`app_name`) need manual updates too.
