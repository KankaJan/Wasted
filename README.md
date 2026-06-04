# Wasted

*(former repository: NextTimeEmail · package `com.nexttimeemail`)*

A minimalistic Android app that tells you, in real time, how much a meeting is
costing — then helps you ask the obvious question: *we wasted this much… wouldn't
an email have been better next time?*

## What it does

1. **Build the roster.** Tap **+** to add attendees. Each has a name, an
   optional email, and a rate — either **hourly** or **manday** (a manday is
   treated as 8 working hours). One **currency** is shared by everyone, typed
   in freely on the roster screen (e.g. `USD`, `EUR`, `CZK`). Optionally set a
   **buzz-every** threshold so your phone vibrates each time the cost crosses a
   multiple of it (100 → buzz at 100, 200, 300…).
2. **Start the meeting.** A big counter shows the accumulating cost, ticking
   once per second, alongside the elapsed time. You can pause/resume, and the
   reminder buzzes at each threshold step.
3. **End the meeting.** The final cost is frozen and the meeting is saved to
   history. One tap opens your email app pre-filled with:

   > Meeting <date> cost <price>. Was it worth it? Wouldn't an email have been
   > better next time?

   …addressed to every attendee who has an email.

All attendees share a single currency, so the cost is one plain figure that
gets the currency symbol only when displayed.

## How it's built

- **Kotlin + Jetpack Compose + Material 3** (dynamic color on Android 12+)
- **MVVM** with `ViewModel` + `StateFlow`; the live cost is driven by a
  coroutine ticking every second, with elapsed time derived from a monotonic
  clock so pausing stays accurate
- **Room** persists both the attendee roster and finished-meeting history
- **Email** is sent via a `mailto:` `Intent` — no credentials, no network
  permission, no backend; the user reviews and sends from their own mail app
- **Localised** in English and Czech (`values/` + `values-cs/`)

### Module layout

```
app/src/main/java/com/nexttimeemail/
├── data/        Room entities, DAOs, database, repository
├── domain/      CostCalculator (pure cost maths + money formatting)
├── util/        time formatting, mailto Intent
└── ui/          Compose screens (roster, meeting, history) + theme + nav
```

## Building & running

Requires the Android SDK (compileSdk 35) and JDK 17+.

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # install on a connected device/emulator
./gradlew test               # run the JVM unit tests (cost maths)
```

Or just open the project in Android Studio and hit Run.

- **minSdk:** 26 (Android 8.0) · **targetSdk / compileSdk:** 35
