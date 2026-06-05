# Wasted — Chrome extension

A parallel, browser version of the **Wasted** Android app: track the live cost of
a meeting from attendees' hourly/man-day rates and email the verdict. Pure
Manifest V3, no build step, no dependencies — load the folder as-is.

## Load it (developer mode)
1. Open `chrome://extensions`.
2. Toggle **Developer mode** (top right).
3. **Load unpacked** → select this `extension/` folder.
4. Pin **Wasted** and click the toolbar icon.

## What works
- Add/remove attendees (name, optional email, hourly **or** man-day rate).
- One global currency (free text) + optional **Buzz every** threshold.
- **Start** → live cost counter (1 s) and elapsed timer while the popup is open;
  pause/resume; **End** → saved to history + a pre-filled `mailto:` email.
- Toolbar **badge** shows the running cost while a meeting is active.
- **Threshold reminders** fire as desktop notifications at each multiple.
- History; English + Czech (`_locales/`).

## How it maps from Android
| Android | Here |
| --- | --- |
| `CostCalculator` | `src/cost.js` (identical maths, unit-tested) |
| Room + `SettingsStore` | `chrome.storage.local` (`src/store.js`) |
| `MeetingEngine` timer | timestamps in storage; popup ticks while open |
| Foreground-service notification | toolbar badge + `chrome.notifications` |
| Vibration buzz | desktop notification |
| `mailto:` Intent | `chrome.tabs.create({ url: 'mailto:…' })` |

### The MV3 background note
Service workers are ephemeral, so nothing ticks every second in the background.
Cost is deterministic (`rate × elapsed`), so:
- the **popup** computes the smooth 1 s counter from a stored `startTimestamp`;
- the **service worker** (`src/background.js`) keeps the badge fresh via a coarse
  repeating `chrome.alarms`, and schedules a **precise one-shot alarm** for the
  exact moment the cost crosses the next threshold to fire the buzz notification.

## Develop
- `node --test` — runs the cost-logic unit tests (`test/`).
- `python3 icons/generate_icons.py` — regenerates the PNG icons.
- No bundler: edit files and hit **Reload** on the extensions page.

## Not yet (ideas)
- Content-script overlay that shows the live cost directly on a Google Meet /
  Zoom-web / Teams call tab.
- Options page; richer history; CSV export.
