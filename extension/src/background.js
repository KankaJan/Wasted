// Service worker: the "foreground service" equivalent. MV3 workers are ephemeral,
// so nothing ticks every second here — instead we derive everything from the stored
// meeting's timestamps and use chrome.alarms to wake up:
//   - "badge"  : a coarse repeating alarm that keeps the toolbar badge fresh
//   - "buzz"   : a one-shot alarm scheduled for the exact moment the cost crosses
//                the next reminder threshold, which fires a desktop notification

import { getMeeting, setMeeting } from "./store.js";
import { elapsedMillis, costAtElapsed, reminderStep, formatMoney } from "./cost.js";
import { isActive } from "./meeting.js";

const INK = "#1A1A1A";

function compactMoney(cost) {
  const n = Math.floor(cost);
  if (n < 1000) return String(n);
  if (n < 100000) return Math.floor(n / 1000) + "k";
  return "99k+";
}

async function refresh() {
  const meeting = await getMeeting();

  if (!isActive(meeting)) {
    await chrome.action.setBadgeText({ text: "" });
    await chrome.alarms.clear("badge");
    await chrome.alarms.clear("buzz");
    return;
  }

  const now = Date.now();
  const ms = elapsedMillis(meeting, now);
  const cost = costAtElapsed(meeting.perHour, ms);

  // Badge + tooltip.
  await chrome.action.setBadgeBackgroundColor({ color: INK });
  await chrome.action.setBadgeText({ text: meeting.running ? compactMoney(cost) : "II" });
  await chrome.action.setTitle({ title: `Wasted — ${formatMoney(cost, meeting.currency)}` });

  // Fire a buzz for every threshold step we have passed but not yet announced.
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
    iconUrl: "icons/icon128.png",
    title: "Wasted",
    message: `This meeting just passed ${amountLabel}. Was it worth it?`,
    priority: 1,
  });
}

async function scheduleAlarms(meeting, cost) {
  // Keep the badge fresh roughly once a minute while running.
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

chrome.alarms.onAlarm.addListener(() => {
  refresh();
});

// React immediately when the popup starts/pauses/ends a meeting.
chrome.storage.onChanged.addListener((changes, area) => {
  if (area === "local" && changes.meeting) refresh();
});

chrome.runtime.onStartup.addListener(refresh);
chrome.runtime.onInstalled.addListener(refresh);
