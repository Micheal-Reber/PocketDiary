<div align="center">

# 📖 PocketDiary

**A minimal, delightful Android diary app · Liquid-glass UI · 100% offline, data stays on your device**

![Platform](https://img.shields.io/badge/platform-Android_8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3.21-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285F4?logo=jetpackcompose&logoColor=white)
![Room](https://img.shields.io/badge/Room-v16-3DDC84)
![Version](https://img.shields.io/badge/version-1.16.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)

**English** | [简体中文](README.zh-CN.md)

**[⬇️ Download](#-download)** · **[✨ Features](#-features)** · **[🛠️ Tech Stack](#-tech-stack)** · **[🙏 Acknowledgements](#-acknowledgements)**

</div>

---

## At a Glance

Five bottom tabs — everything the app does, in one place:

| Tab | What's inside |
|-----|---------------|
| **Diary** | One entry per day · mood / weather / location · Markdown · photo posts · full-text search · blurred custom background · PIN lock |
| **Calendar** | Habit check-in month view · colored dots · back-fill · weekly / monthly / yearly charts |
| **Countdown** | Classic fullscreen & photo-card styles · three-state date math · share cards · **home-screen widget** |
| **Todos** | Dark folded list · exact-alarm & lock-screen ringer reminders · daily repeat · snooze |
| **Focus** | Pomodoro timer · focus tasks & sessions · white noise · strong end-of-focus alert · app lock during focus · focus statistics |

Three promises: **your data never leaves the device**; **one-tap migration to a new phone**; **the glass UI is real**, not a texture.

---

## ✨ Features

### 📖 Diary
- **One entry per day**, auto-archived by date (`date` unique index; changing the date moves the entry), split by month with big day numbers
- Record **mood, weather, and location** — one-tap geocoding to a readable address; weather is picked manually
- **Markdown writing & preview**: commonmark parser (plus the table / strikethrough / task-list GFM extensions) with a custom subset renderer; the format bar only emits actions, never touches the text
- **Rich photo posts** with up to 7 inline images, two-phase lifecycle management, fullscreen viewer
- **Full-text search** (250 ms debounce), **swipe-to-delete** with a confirmation dialog, two-line card previews
- **Custom list background**: import a photo in Settings and every card gets a real **backdrop blur** (GraphicsLayer + BlurEffect, API 31+)
- **Diary PIN lock**: 4–6 digits, three unlock modes (per session / every visit / once per day), re-auth to change the PIN, SHA-256 salted hashing, 30 s lockout after 5 failed attempts

### 📅 Habit Calendar
- Monthly calendar with a full habit check-in system; each day cell shows **colored dots** for completed habits (up to 3)
- Tap any date to tick items one by one — **past days can be back-filled**, future days are locked
- Habit names and emoji icons are fully customizable
- **Statistics screen** in three views: **weekly frequency** (rolling 10-week window) / **monthly** / **yearly**
- Every point on the line chart carries a **value label**; the current period is **highlighted**; toggle habits at the bottom to show/hide curves

### ⏳ Countdown
- **Classic fullscreen** and **photo card** styles; blue/orange badges, pin-to-top, yearly/monthly repeat
- **Correct three-state date math** (Today / Countdown / Countup — month- and leap-year-safe, +1 day semantics)
- **Off-screen rendered share cards** (1080×1440), per-event background photos; photo cards support vertical reframing by drag
- List / grid views, procedural texture backdrops (no bitmap assets)

### 🖥️ Home-screen Widget
- Countdown widget built on **pure RemoteViews** (no Glance / WorkManager), verified to render on MIUI launchers
- Single-level flat view tree, fixed font sizes, zero-bitmap background (pure black, 28 dp corners) — sidesteps MIUI's nested-view truncation and bitmap sizing traps
- Shows one event: your pick, or automatically the pinned / nearest one; tapping opens the picker, not the detail page
- Refreshes every 30 minutes, plus on unlock, clock change, timezone change, and midnight

### ✅ Todos
- Dark list with **completed items folded away**; checking an item sinks/rises it; edit directly in a sheet (no separate page)
- **Exact-alarm reminders**: notification mode or **full-screen alarm-ringer mode** (`setAlarmClock`, rings on the lock screen), daily repeat, 5-minute snooze
- **Automatic rescheduling** after reboot / timezone or clock change, with missed plain notifications re-posted; MIUI auto-start detection with a deep link to the permission page
- Notifications and ringer deep-link straight to the todo (`open_todo_id`)

### 🍅 Focus (Pomodoro)
- **Focus tasks & sessions**: per-task totals, sessions and task credits written in the same transaction so nothing is lost if the process dies
- Focus / short break / long break lengths and long-break interval are all configurable; **daily goal accepts a custom minute count** (10–720)
- **White noise**: white / pink / brown procedurally synthesized (zero assets, zero seams, unified RMS), or **import your own audio** (copied into the app directory, no external file dependency)
- **Strong end-of-focus alert**: rings + vibrates + turns the screen on only when a focus block ends (auto-stops after 60 s); a finished break only posts a notification. Ringtone, volume, vibrate on/off, duration, gap, intensity and screen-on are all configurable, with a stop button on the notification while it rings
- **Lock other apps during focus**: an accessibility service reads the foreground package and pulls you back; a whitelist switches freely (IME always allowed), searchable across system / installed apps, with a visible **pull-back count**
- **Ongoing notification + Android 16 live activity**: a status-bar countdown pill whose walked portion is bold with theme-following segments
- **Focus statistics**: daily-goal dial, 7-day and 26-week heatmaps, weekly / monthly / yearly trends with period-over-period deltas, and a per-task breakdown (tap a row to start that task's Pomodoro)
- **Keep-alive guidance**: detects battery-optimization whitelist status with a one-tap join, then points MIUI / HyperOS users at auto-start — so the system can't kill the timer mid-session
- Timing uses `SystemClock.elapsedRealtime`, so it survives reboots and clock changes

### ⚙️ Settings
- The Settings tab is a **directory page** opening five sub-pages — **Appearance / Todos / Privacy / Migration / About** — with current values summarised on each row
- Dark / light mode **independent of the system**, splash color follows the in-app theme; **Material You wallpaper colors**
- **Bottom bar mode**: floating glass capsule (default) or classic docked bar, one toggle
- **Language**: 中文 / English / follow system
- **PIN lock** and **system-calendar sync** toggles
- **Data migration**: export / import a ZIP; format v4 splits preferences into Appearance / Focus / Lock / Calendar groups and **carries your custom white-noise audio along**; import only overwrites groups the backup actually contains (older backups still import and won't reset your Pomodoro settings to defaults); full overwrite + transaction + `*.bak` rollback + version gate + zip-slip / CRC checks
- About page version reads `BuildConfig.VERSION_NAME` automatically

### 🎨 Design Language
- **True liquid glass**: the floating capsule bottom bar and FAB sample the content behind them for a live backdrop blur ([`io.github.kyant0:backdrop`](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.6)
- Card hierarchy uses `surfaceContainerLow/High` tones instead of shadows; every radius and spacing value comes from design tokens — no magic numbers in code

---

## 📥 Download

Grab the latest `app-release.apk` from the [**Releases page**](https://github.com/Micheal-Reber/PocketDiary/releases) and install it on your phone (allow installation from unknown sources).

> Requires **Android 8.0 (API 26)+**; the full glass/backdrop-blur effects render on **Android 12 (API 31)+**.

## 🛠️ Tech Stack

| Area | Choice |
|------|--------|
| Language | Kotlin 2.3.21 |
| UI | Jetpack Compose BOM 2026.01.01 (Compose 1.10.2 / Material 3 1.4) + liquid glass [`backdrop`](https://github.com/Kyant0/AndroidLiquidGlass) 1.0.6 |
| Database | Room 2.8.5 · **v16, seven tables** (Diary / Habit / HabitRecord / Countdown / Todo / FocusTask / FocusSession), handwritten migration chain v9→16 |
| Preferences | DataStore Preferences ×5 (theme / language / PIN lock / focus / calendar) |
| Navigation | Navigation Compose (5 bottom tabs + sub-routes) |
| Architecture | ViewModel + Repository + Flow unidirectional data flow |
| Location | Stock LocationManager (no Play Services dependency) |
| Backup | kotlinx-serialization JSON + streaming ZIP (STORED CRC + version gate v1..v4) |
| Reminders | AlarmManager (`setExactAndAllowWhileIdle` / `setAlarmClock`) + reboot/timezone/clock rescheduling |
| Markdown | org.commonmark 0.24.0 + three GFM extensions (AST only) + custom subset renderer |
| Widget | Pure RemoteViews (no Glance / WorkManager) |
| Services | Foreground service (focus timer) + accessibility service (app lock, foreground package only) |
| Build | **KSP** for Room (not kapt) |
| Splash | Plain-color system splash (no logo, follows the in-app theme) |

## 🚀 Build

1. Install **JDK 17** and the Android SDK (compileSdk 36)
2. Set `JAVA_HOME` and `ANDROID_HOME`
3. Signing (optional): put `pocketdiary.jks` in the repo root plus a gitignored `keystore.properties` (`storePassword`/`keyAlias`/`keyPassword`)
4. Run:

```bash
./gradlew test               # JVM unit tests (run before every release)
./gradlew assembleDebug      # debug build
./gradlew assembleRelease    # signed release build (when keystore.properties exists)
```

> **Debug and release use different signatures** — overwriting an install requires uninstalling the old build first (data will be wiped).

## 📂 Project Structure

```
app/src/main/java/com/example/diary/
├── MainActivity.kt         # entry: in-app theme override (independent of system) + open_todo_id deep link
├── DiaryApplication.kt
├── receiver/               # todo alarms + reboot/clock-change rescheduling
├── util/                   # DateUtils shared date/reminder formatting
├── data/
│   ├── backup/             # backup format v4 + export/import services (version gate, zip-slip protection)
│   ├── countdown/          # date-math pure functions + share-card renderer + procedural textures
│   ├── focus/              # pomodoro timer logic + foreground service + white-noise synthesis + app lock + stats
│   ├── image/              # background image store (relative paths) + per-event backgrounds
│   ├── local/              # Room v16: seven entities + DAOs + MIGRATION_* chain
│   ├── location/           # LocationManager wrapper (no GMS)
│   ├── photo/              # DiaryPhotoStore two-phase lifecycle for photo posts
│   ├── preferences/        # DataStore ×5: theme / language / PIN lock / focus / calendar
│   ├── repository/         # repository layer (SaveResult for date conflicts)
│   └── todo/               # alarm scheduling + notifications + full-screen ringer
└── ui/
    ├── components/         # shared: swipe-delete / confirm / search / date picker / preset chips
    ├── countdown/          # countdown list / edit / detail + widget picker
    ├── diary/              # diary list: month split, swipe delete, blurred background, search
    ├── editor/             # editor: borderless writing, Markdown preview & format bar, photo posts
    ├── focus/              # focus screen + task components + focus statistics
    ├── habits/             # habit calendar + statistics charts (self-built LineChart) + ViewModel
    ├── lock/               # PIN lock keypad
    ├── navigation/         # bottom tabs + routes + liquid-glass bottom bar (dual mode) + glass primitives
    ├── settings/           # directory page + Appearance / Todos / Privacy / Migration / About
    ├── theme/              # Material 3 theme + radius/spacing tokens
    ├── todo/               # todos list + edit/reminder sheets + ring activity
    └── widget/             # countdown home-screen widget (RemoteViews)
```

## 🧪 Tests

**19 test classes / 195 JVM unit tests**, all run by `./gradlew test`:

| Area | Test classes |
|------|--------------|
| Dates & reminders | `DateMathTest` · `TodoReminderSchedulerTest` · `CountdownReminderTest` |
| Backup & migration | `BackupSerializationTest` · `BackupVersionGateTest` · `Migration15To16Test` (Robolectric) |
| Focus | `FocusTimerTest` · `FocusStatsTest` · `WhiteNoiseTest` · `FocusGuardTest` · `FocusAlarmRingerTest` · `FocusNotificationPlannerTest` · `BackgroundStartTest` |
| Todos | `TodoRepositoryTest` (Robolectric) |
| Widget | `CountdownWidgetModelTest` |
| Markdown | `MarkdownEditingTest` · `MarkdownPlainTextTest` · `MarkdownExtParseTest` |
| Rendering | `BlurCacheTest` |

## 🙏 Acknowledgements

- **[Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass)** — PocketDiary's glass surfaces are built on its `backdrop` library (`io.github.kyant0:backdrop`). The refraction / chromatic-dispersion shader, rim highlight, inner shadow, and the sample catalog's `LiquidBottomTabs` were the reference for our floating bottom bar, FAB and glass cards. Thanks for the library and the catalog.
- **[perchA5uka/Niriko](https://github.com/perchA5uka/Niriko)** — its `docs/dock-kyant-migration-plan.md` and `ui/bottombar` package (MIT) documented the pitfalls of porting the floating capsule dock: hit-cell vs. indicator coordinate spaces, gesture-layer ownership, explicit indicator width. Those notes shaped our implementation.

## 📄 License

[MIT](LICENSE) © Micheal-Reber
