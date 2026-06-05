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
Service workers are ephemeral. Cost is deterministic (`rate × elapsed`), so:
- the **popup** computes the smooth **1 s** counter and refreshes the toolbar
  badge every second while it is open;
- the **service worker** (`src/background.js`) keeps the badge live in the
  background by running a ~**10 s** `setInterval` whose periodic `chrome.action`
  calls also keep the worker alive; a 1-minute `chrome.alarms` backstop restarts
  that ticker if the worker was terminated;
- a **precise one-shot alarm** is scheduled for the exact moment the cost crosses
  the next threshold to fire the buzz notification.

So the badge updates every second with the popup open and roughly every 10 s with
it closed — Chrome won't allow a true 1 s background loop in MV3.

## Develop
- `node --test` — runs the cost-logic unit tests (`test/`).
- `python3 icons/generate_icons.py` — regenerates the PNG icons.
- No bundler: edit files and hit **Reload** on the extensions page.

## Not yet (ideas)
- Content-script overlay that shows the live cost directly on a Google Meet /
  Zoom-web / Teams call tab.
- Options page; richer history; CSV export.
