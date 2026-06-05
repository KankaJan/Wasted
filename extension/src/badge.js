// Shared toolbar-badge renderer, used live (every second) by the popup and on
// each wake by the service worker. chrome.action is available in both contexts.

import { elapsedMillis, costAtElapsed, formatMoney } from "./cost.js";

const INK = "#1A1A1A";

/** Badge text is capped at ~4 chars, so show a compact whole-number cost. */
export function compactMoney(cost) {
  const n = Math.floor(cost);
  if (n < 1000) return String(n);
  if (n < 100000) return Math.floor(n / 1000) + "k";
  return "99k+";
}

/** Reflects the meeting cost on the toolbar icon; clears it when not running. */
export async function renderBadge(meeting) {
  if (!meeting || meeting.phase !== "RUNNING") {
    await chrome.action.setBadgeText({ text: "" });
    await chrome.action.setTitle({ title: "Wasted" });
    return;
  }
  const cost = costAtElapsed(meeting.perHour, elapsedMillis(meeting));
  await chrome.action.setBadgeBackgroundColor({ color: INK });
  await chrome.action.setBadgeText({ text: meeting.running ? compactMoney(cost) : "II" });
  await chrome.action.setTitle({
    title: `Wasted — ${formatMoney(cost, meeting.currency)}${meeting.running ? "" : " (paused)"}`,
  });
}
