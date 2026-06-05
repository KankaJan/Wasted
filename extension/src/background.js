// Service worker: the "foreground service" equivalent. MV3 workers are ephemeral,
// so to keep the toolbar badge live while a meeting runs we:
//   - run a short setInterval whose periodic chrome.action calls also keep the
//     worker alive (resets the ~30s idle timer) -> ~10s badge updates in the
//     background even with the popup closed;
//   - keep a 1-minute "badge" alarm as a backstop that restarts the ticker if
//     the worker was nonetheless terminated;
//   - schedule a precise one-shot "buzz" alarm for the exact moment the cost
//     crosses the next reminder threshold (desktop notification).
// The popup additionally refreshes the badge every second while it is open.

import { getMeeting, setMeeting } from "./store.js";
import { elapsedMillis, costAtElapsed, reminderStep, formatMoney } from "./cost.js";
import { isActive } from "./meeting.js";
import { renderBadge } from "./badge.js";

const TICK_MS = 10_000;
let badgeTimer = null;

function ensureTicker() {
  if (badgeTimer == null) badgeTimer = setInterval(refresh, TICK_MS);
}
function stopTicker() {
  if (badgeTimer != null) {
    clearInterval(badgeTimer);
    badgeTimer = null;
  }
}

async function refresh() {
  const meeting = await getMeeting();
  await renderBadge(meeting);

  if (!isActive(meeting)) {
    stopTicker();
    await chrome.alarms.clear("badge");
    await chrome.alarms.clear("buzz");
    return;
  }

  // Keep the worker alive (and the badge ticking) only while actually accruing.
  if (meeting.running) ensureTicker();
  else stopTicker();

  const cost = costAtElapsed(meeting.perHour, elapsedMillis(meeting));

  // Buzz backstop — the precise "buzz" alarm normally handles this first.
  if (meeting.threshold > 0 && meeting.running) {
    const step = reminderStep(cost, meeting.threshold);
    if (step > (meeting.lastBuzzStep || 0)) {
      notify(formatMoney(step * meeting.threshold, meeting.currency));
      meeting.lastBuzzStep = step;
      await setMeeting(meeting);
    }
  }

  await scheduleAlarms(meeting, cost);
}

function notify(amountLabel) {
  chrome.notifications.create({
    type: "basic",
    // Must be an absolute extension URL from a service worker; a relative path
    // fails with "Unable to download all specified images".
    iconUrl: chrome.runtime.getURL("icons/icon128.png"),
    title: "Wasted",
    message: `This meeting just passed ${amountLabel}. Was it worth it?`,
    priority: 1,
  });
}

async function scheduleAlarms(meeting, cost) {
  // Backstop badge refresh that also restarts the ticker if the worker died.
  if (meeting.running) {
    await chrome.alarms.create("badge", { periodInMinutes: 1 });
  } else {
    await chrome.alarms.clear("badge");
  }

  // Schedule the next threshold crossing precisely.
  await chrome.alarms.clear("buzz");
  if (meeting.threshold > 0 && meeting.running && meeting.perHour > 0) {
    const nextStep = (meeting.lastBuzzStep || 0) + 1;
    const targetCost = nextStep * meeting.threshold;
    const remaining = targetCost - cost;
    if (remaining > 0) {
      const msToNext = (remaining / meeting.perHour) * 3_600_000;
      chrome.alarms.create("buzz", { when: Date.now() + Math.max(1000, msToNext) });
    }
  }
}

chrome.alarms.onAlarm.addListener(refresh);

// React immediately when the popup starts/pauses/ends a meeting.
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.meeting) refresh();
});

chrome.runtime.onStartup.addListener(refresh);
chrome.runtime.onInstalled.addListener(refresh);
