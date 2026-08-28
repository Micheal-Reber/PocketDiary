# Data Export/Import for Phone Migration - Work Plan

## TL;DR
Implement export/import functionality to allow users to migrate all PocketDiary data (diary entries, habits, countdown events, photos, preferences) when switching phones. Export creates a zip file with all data; import restores everything on new device.

---

## TL;DR (machine)
Medium/High — 8 todos across 4 waves: data model (serialization) → export service (zip with DB + images + prefs) → import service (parse zip → restore DB + images + prefs) → UI in Settings → QA

---

## Scope

### Must Have
1. **Export**: Create a `.zip` file containing:
   - `data.json` — all Room entities (DiaryEntry, Habit, HabitRecord, CountdownEvent)
   - `preferences.json` — DataStore preferences (dark_mode, diary_background_path, dynamic_color, editor_preview)
   - `images/` — all image files organized by type:
     - `images/diary_photos/<entryId>/*.jpg` (diary photos)
     - `images/countdown_backgrounds/bg_<eventId>.jpg` (countdown backgrounds)
     - `images/diary_background.jpg` (single diary background)

2. **Import**: Parse zip, restore:
   - Room database (all 4 tables) with conflict resolution
   - Image files to correct `filesDir` locations
   - DataStore preferences

3. **UI**: Export/Import buttons in Settings screen with file picker (SAF)

### Must NOT Have
- ❌ Cloud sync / cloud backup (local-only, privacy-first)
- ❌ Encryption (phase 2 — keep v1 simple, user owns the zip file)
- ❌ Selective export (all-or-nothing for v1 — simpler, safer)
- ❌ Auto-backup / scheduled backup (manual only for v1)

---

## Data Model & Scope

### Data to Export/Import

| Category | Entities | Storage |
|---|---|---|
| **Diary** | `DiaryEntry` | Room `diary_entries` |
| **Habits** | `Habit`, `HabitRecord` | Room `habits`, `habit_records` |
| **Countdown** | `CountdownEvent` | Room `countdown_events` |
| **Images (Diary)** | Files in `filesDir/diary_photos/<entryId>/` | File system |
| **Countdown Backgrounds** | `countdown_backgrounds/bg_<eventId>.jpg` | File system |
| **Diary Background** | `backgrounds/diary_background.jpg` | File system |
| **Preferences** | DataStore (`settings`) | DataStore |

### Serialization Format
- `data.json` — JSON array per entity type (using kotlinx-serialization)
- `preferences.json` — flat JSON object
- `images/` — directory tree mirroring `filesDir` structure
- Archive: `.zip` (no compression needed for images, but zip for single-file transfer)

---

## Architecture

### New Files
```
data/
  backup/
    BackupData.kt          # @Serializable data classes (BackupData, PreferencesData, ImageFileInfo, ImportResult)
    ExportService.kt       # Collects all data → creates zip
    ImportService.kt       # Parses zip, restores DB + images + prefs
    BackupRepository.kt    # High-level orchestration (export/import orchestration)
```

### Existing Files to Modify
- `SettingsScreen.kt` — Add Export/Import buttons, file picker (SAF)
- `AppDatabase.kt` — Add helper suspend functions for bulk insert with conflict resolution
- `DiaryRepository`, `CountdownRepository`, `HabitRepository` — add bulk insert helpers
- `ThemePreferences` — add import/export helpers

---

## Implementation Plan

### Wave 1: Data Model & Serialization (T1)
- [x] `BackupData.kt` — `@Serializable` data classes (`BackupData`, `PreferencesData`, `ImageFileInfo`, `ImportResult`)
- [x] Add `kotlinx-serialization` + `kotlinx-serialization-json` dependencies if missing
- [ ] Unit tests for serialization round-trip

### Wave 2: Export Service (T2)
- [x] `ExportService.kt`:
  - `suspend fun export(context: Context, outputFile: File): Result<File>`
  - Collect all Room entities via repositories (suspend functions)
  - Collect DataStore preferences via `ThemePreferences`
  - Walk `filesDir` for image directories, copy to zip `images/`
  - Create zip using `java.util.zip` (store compression for images)
  - Save zip to user-selected location via SAF (`ACTION_CREATE_DOCUMENT`)

### Wave 3: Import Service (T3)
- [x] `ImportService.kt`:
  - `suspend fun import(context: Context, inputFile: File): ImportResult`
  - Open zip via SAF (`ACTION_OPEN_DOCUMENT`)
  - Parse `data.json` → deserialize entities
  - Parse `preferences.json` → restore DataStore
  - Extract `images/` to `filesDir` preserving directory structure
  - **Database import strategy**: 
    - Use `OnConflictStrategy.REPLACE` for DiaryEntry (date unique)
    - `OnConflictStrategy.REPLACE` for Habit (id PK)
    - `OnConflictStrategy.REPLACE` for HabitRecord (habitId+date unique)
    - `OnConflictStrategy.REPLACE` for CountdownEvent (id PK)
  - Delete existing images for imported entities before copying new ones
  - Transactional: wrap DB operations in `runInTransaction`

### Wave 4: UI Integration (T4)
- [x] `SettingsScreen.kt`:
  - Add "导出数据" / "导入数据" buttons in a new "数据迁移" section
  - Export: `ACTION_CREATE_DOCUMENT` (mime `application/zip`, title "PocketDiary备份")
  - Import: `ACTION_OPEN_DOCUMENT` (mime `application/zip`)
  - Show progress dialog during export/import
  - Show result toast/snackbar with counts

### Wave 5: Polish & QA (T5)
- [ ] Handle edge cases: empty DB, missing images, corrupt zip, version mismatch
- [ ] Unit tests: serialization round-trip, import conflict resolution
- [ ] Integration test: full export→import round-trip on test device
- [ ] Update `AGENTS.md` with backup module documentation

---

## Key Technical Decisions

| Decision | Choice | Rationale |
|---|---|---|
| **Conflict Strategy** | `OnConflictStrategy.REPLACE` for all entities | Simpler; user expects "restore" semantics; data loss acceptable for migration |
| **Image Handling** | Copy files to `filesDir` preserving relative paths | Matches existing `EventImageStore`/`BackgroundImageStore` conventions |
| **Preferences** | Overwrite all DataStore keys from backup | Simpler than merge; user expects full restore |
| **Versioning** | `version: Int = 1` in `BackupData` | Future-proof for schema changes |
| **Zip Compression** | `STORED` (no compression) for images, `DEFLATED` for JSON | Images already compressed; saves CPU |

---

## File Changes Summary

### New Files
- `app/src/main/java/com/example/diary/data/backup/BackupData.kt`
- `app/src/main/java/com/example/diary/data/backup/ExportService.kt`
- `app/src/main/java/com/example/diary/data/backup/ImportService.kt`
- `app/src/main/java/com/example/diary/data/backup/BackupRepository.kt`

### Modified Files
- `app/build.gradle.kts` — add `kotlinx-serialization-json`
- `app/src/main/java/com/example/diary/ui/settings/SettingsScreen.kt` — Export/Import UI
- `app/src/main/java/com/example/diary/data/repository/DiaryRepository.kt` — add `getAllEntriesSuspend()`, `insertAll()`
- `app/src/main/java/com/example/diary/data/repository/CountdownRepository.kt` — add `insertAll()`
- `app/src/main/java/com/example/diary/data/repository/HabitRepository.kt` — add `insertAllHabits()`, `insertAllRecords()`
- `app/src/main/java/com/example/diary/data/preferences/ThemePreferences.kt` — add `exportPreferences()`, `importPreferences()`
- `app/src/main/java/com/example/diary/data/local/AppDatabase.kt` — add bulk insert helpers

---

## Verification Strategy

| Check | Method |
|---|---|
| Unit tests | Serialization round-trip, import conflict resolution |
| Integration | `assembleRelease` + `testDebugUnitTest` |
| Device QA | Export → uninstall app → reinstall → import → verify all data |
| Edge cases | Empty DB, missing images, corrupt zip, version mismatch |

---

## Acceptance Criteria (Final)

1. **Export**: Creates valid `.zip` with all data + images, openable on PC
2. **Import**: Restores all diary entries, habits, countdowns, photos, preferences
3. **Regression**: Classic countdown style, habits, diary entries unchanged after import
4. **User flow**: Settings → 导出数据 → 选择保存位置 → 导入数据 → 选择备份文件 → 恢复完成

---

## Open Questions (for user confirmation)

1. **Conflict resolution**: Use `REPLACE` (overwrite) for all entities on import? Or skip existing?
   - Current plan: `REPLACE` (simpler, migration = overwrite)

2. **Image deduplication**: Skip images that already exist (by path + size)?
   - Plan: Always overwrite (simpler, migration = fresh state)

3. **Version code bump**: Should `versionCode` increment to 4 for this release?
   - Current: 2 (v1.2) → Next: 3 (v1.3) or 4 (v1.4)?

---

## Execution Order

```
T1: BackupData.kt (serialization) 
→ T2: ExportService (zip creation)
→ T3: ImportService (zip parsing + restore)
→ T4: Settings UI (export/import buttons)
→ T7: AGENTS.md + build + user test
→ User tests → says "推送" → commit + push + tag v1.4
```

---

### 确认开工？

回复 **"开工"** 我将开始执行 T1 (BackupData.kt 序列化层)，随后依次完成 T2-T7，最后等待你实测确认后提交推送。