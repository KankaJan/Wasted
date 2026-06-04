# Handover — NextTimeEmail

A minimalistic Android app that shows the live, ticking cost of a meeting based
on attendees' rates, and on "End Meeting" opens a pre-filled email asking whether
it was worth it. This document is the pickup point for finishing the build on a
machine (or container) that has the Android SDK and network access.

> **Why this doc exists:** the environment where the app was written had **no
> Android SDK and no outbound network** (Google's SDK host and the Maven/AGP
> repositories are blocked), so the project was written and hand-reviewed but
> **never compiled or run**. Everything below is what's needed to take it the
> last mile.

---

## 1. Current state

**Done (committed & pushed to `claude/meeting-cost-calculator-IHc7w`):**

- Full Kotlin + Jetpack Compose + Material 3 app, MVVM architecture.
- Roster screen: add/edit/delete attendees (name, optional email, hourly **or**
  manday rate — manday = 8 h). One global **currency** (free text) and an
  optional **buzz-every** reminder threshold, both on the roster screen.
- Live meeting screen: cost counter ticking every 1 s (elapsed time from a
  monotonic clock so pause/resume is accurate), pause/resume, end.
- End/result: final cost frozen, meeting saved to history, **mailto:** email
  button (no credentials/network/backend — opens the user's mail app).
- History screen: past meetings with date, duration, attendee count, cost.
- Room persistence (roster + history), `SettingsStore` (SharedPreferences) for
  currency + reminder threshold.
- Optional vibration buzz each time cost crosses a multiple of the threshold.
- Localised **English + Czech**.
- JVM unit tests for the cost maths (`CostCalculatorTest`).
- Gradle wrapper (8.11.1) committed.

**Verified:**

- ✅ **Cost-engine unit tests pass (7/7).** The Android build can't run in the dev
  container (Google's Maven is blocked — see below), so the pure-Kotlin logic
  (`CostCalculator` + `Attendee.hourlyRate`) was compiled and tested in a
  standalone JVM Gradle project against Maven Central. All assertions pass,
  including currency formatting.
- ✅ **GitHub Actions CI** (`.github/workflows/android.yml`) runs the real
  `testDebugUnitTest` + `lintDebug` + `assembleDebug` on every push/PR using a
  runner that has the SDK and full network — this is the authoritative build.

**Not done / unverified locally:**

- ❗ **The Android layer (UI/data/Room/Compose) has not been compiled here** — the
  dev container blocks `dl.google.com` / `maven.google.com`, where AGP, AndroidX,
  Compose and Room live (Maven Central *is* reachable). It is hand-audited
  (imports, icon refs, OptIns, `collect` import all checked) and is built by CI.
- ❗ No instrumented (device) tests, no screenshots, no signed release.
- ❗ Launcher icon is a simple vector clock placeholder.

### Network policy note (important)
The dev container's policy allows **Maven Central, Gradle services, the Gradle
Plugin Portal**, but blocks **Google's Maven** (`dl.google.com`,
`maven.google.com`). To build locally/in a container you need a policy that also
allows Google's Maven, or just rely on GitHub Actions (whose runners are
unrestricted).

---

## 2. Finish it — step by step

### Prerequisites
- JDK 17+ (the repo was wrapped with Gradle 8.11.1; AGP 8.7.3 needs JDK 17).
- Android SDK with **platform 35**, **build-tools 35.0.0**, **platform-tools**.
- Network access to `dl.google.com` (SDK) and `dl.google.com`/`repo.maven.apache.org`
  (AGP + AndroidX + Room/KSP artifacts).

If you open the repo in **Android Studio**, it will offer to install the SDK
pieces and sync automatically — then just press Run. For CI / headless, see the
SessionStart hook in §4 which provisions the SDK.

### Commands
```bash
./gradlew testDebugUnitTest    # run the JVM unit tests (cost maths)
./gradlew lintDebug            # Android lint
./gradlew assembleDebug        # build the debug APK
./gradlew installDebug         # install on a connected device/emulator
```

### Likely first-build friction (and fixes)
1. **`SDK location not found`** → create `local.properties` with
   `sdk.dir=/path/to/Android/sdk` (the hook does this automatically), or set
   `ANDROID_HOME`.
2. **Compose/Material 3 API mismatch** — a few newer APIs are used and pinned via
   the version catalog (`gradle/libs.versions.toml`): `menuAnchor` was removed
   from the attendee dialog in the latest refactor, but double-check
   `SegmentedButton`, `collectAsStateWithLifecycle`
   (`androidx.lifecycle:lifecycle-runtime-compose`) resolve. If the BOM
   (`composeBom = 2024.12.01`) is unavailable in your mirror, bump to the
   nearest available and re-sync.
3. **KSP version** must track the Kotlin version. Catalog pins
   `kotlin = 2.0.21` with `ksp = 2.0.21-1.0.28`. If you change Kotlin, change KSP
   to the matching `<kotlin>-<ksp>` build.
4. **Room schema** — DB is at **version 2** with `fallbackToDestructiveMigration()`
   (the per-attendee currency column was dropped). No released data, so a clean
   wipe on upgrade is intentional.

### Definition of done
- [x] Cost-engine unit tests pass (verified standalone; also run in CI).
- [ ] `./gradlew assembleDebug` produces an APK (run by CI — confirm green).
- [ ] `./gradlew lintDebug` clean (or triaged).
- [ ] App runs: add attendees → start → cost ticks → buzz at threshold → end →
      email composer opens with all attendees pre-filled.
- [ ] Replace the placeholder launcher icon (optional).

---

## 3. Architecture map

```
app/src/main/java/com/nexttimeemail/
├── NextTimeEmailApp.kt      Application; holds repository + SettingsStore (manual DI)
├── MainActivity.kt          setContent { theme { AppNavHost() } }
├── data/
│   ├── Attendee.kt          Room entity (name, email?, rateType, rateValue)
│   ├── RateType.kt          HOURLY | MANDAY (+ HOURS_PER_MANDAY = 8)
│   ├── MeetingRecord.kt     Room entity (startedAt, durationMillis, count, costSummary)
│   ├── *Dao.kt              attendee + meeting DAOs (Flow-based)
│   ├── Converters.kt        RateType <-> String
│   ├── AppDatabase.kt       Room DB v2, destructive fallback, singleton
│   ├── MeetingRepository.kt thin DAO wrapper
│   └── SettingsStore.kt     SharedPreferences: currencyCode + reminderThreshold
├── domain/
│   └── CostCalculator.kt    PURE maths: perHourTotal, costAtElapsed, reminderStep,
│                            formatMoney, defaultCurrencyCode   (unit-tested)
├── util/
│   ├── TimeFormat.kt        elapsed HH:MM:SS, localized date/time
│   ├── Email.kt             mailto: Intent builder
│   └── Vibration.kt         buzz() (VibratorManager / Vibrator split)
└── ui/
    ├── AppNavHost.kt        routes: roster | meeting | history
    ├── AppViewModelProvider.kt  viewModelFactory wiring repo + settings
    ├── theme/               Color, Theme (dynamic color), Type (counter styles)
    ├── roster/              RosterScreen, RosterViewModel, AttendeeEditDialog, AmountText
    ├── meeting/             MeetingScreen, MeetingViewModel (timer + buzz events)
    └── history/             HistoryScreen, HistoryViewModel
```

**Key flows**
- **Cost:** all attendees share one currency, so cost is a single `Double`.
  `perHourTotal(attendees)` → `costAtElapsed(perHour, elapsedMillis)` →
  `formatMoney(amount, currencyCode)`.
- **Timer:** `MeetingViewModel` ticks every 1 s in a coroutine, but elapsed is
  computed from `SystemClock.elapsedRealtime()` so it's drift-free and
  pause/resume-safe.
- **Buzz:** on each tick, `reminderStep(cost, threshold)` = `floor(cost/threshold)`;
  when it increments, the VM emits a one-shot event on a `SharedFlow` that
  `MeetingScreen` collects to call `buzz(context)`. Threshold is read once at
  meeting start. Buzzing at most once per second is intentional.

---

## 4. "Complete container" setup (SessionStart hook)

`.claude/hooks/session-start.sh` (registered in `.claude/settings.json`)
provisions a headless container so a future Claude Code (web) session can build:

- Downloads Android command-line tools (if absent) into `$HOME/android-sdk`.
- Accepts SDK licenses; installs `platform-tools`, `platforms;android-35`,
  `build-tools;35.0.0`.
- Exports `ANDROID_HOME`/`ANDROID_SDK_ROOT` via `$CLAUDE_ENV_FILE` and writes
  `local.properties`.
- Pre-warms the Gradle wrapper distribution.

**Network requirement:** the hook needs outbound access to `dl.google.com` and
the Maven/Google artifact repos. Pick an environment network policy that allows
these, otherwise the SDK download and Gradle sync will fail (this is exactly why
the app couldn't be built where it was written).

It is **idempotent** and **remote-only** (`$CLAUDE_CODE_REMOTE`), so it's a
no-op on your local machine — locally just use Android Studio or your own SDK.

---

## 5. Open product questions (nice-to-haves, not blockers)
- Edit the reminder threshold *live* during a meeting (currently locked at start).
- Per-meeting currency override (currently one global currency — by request).
- Meeting history detail / export.
- Real launcher icon and an app screenshot set.
