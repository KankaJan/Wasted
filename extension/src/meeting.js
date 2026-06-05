// Pure meeting-state transitions, shared by the popup and the service worker.
// State shape:
//   { startedAt, baseElapsedMs, lastResumeTs, running, perHour, currency,
//     threshold, recipients[], attendeeCount, lastBuzzStep, phase }

import { perHourTotal, elapsedMillis, costAtElapsed, formatMoney } from "./cost.js";

export function buildMeeting(attendees, currency, threshold) {
  const now = Date.now();
  const recipients = attendees
    .map((a) => (a.email || "").trim())
    .filter((e) => e.length > 0);
  return {
    startedAt: now,
    baseElapsedMs: 0,
    lastResumeTs: now,
    running: true,
    perHour: perHourTotal(attendees),
    currency,
    threshold: Number(threshold) || 0,
    recipients,
    attendeeCount: attendees.length,
    lastBuzzStep: 0,
    phase: "RUNNING",
  };
}

export function pauseToggle(meeting, now = Date.now()) {
  if (!meeting || meeting.phase !== "RUNNING") return meeting;
  if (meeting.running) {
    return { ...meeting, running: false, baseElapsedMs: elapsedMillis(meeting, now) };
  }
  return { ...meeting, running: true, lastResumeTs: now };
}

export function endMeeting(meeting, now = Date.now(), locale = undefined) {
  const ms = elapsedMillis(meeting, now);
  const cost = costAtElapsed(meeting.perHour, ms);
  return {
    meeting: { ...meeting, phase: "ENDED", running: false, baseElapsedMs: ms },
    record: {
      startedAt: meeting.startedAt,
      durationMillis: ms,
      attendeeCount: meeting.attendeeCount,
      costSummary: formatMoney(cost, meeting.currency, locale),
    },
  };
}

export function isActive(meeting) {
  return !!meeting && meeting.phase === "RUNNING";
}
