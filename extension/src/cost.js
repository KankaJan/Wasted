// Shared, framework-free cost maths — a direct port of the Android CostCalculator.
// Pure functions, no DOM/chrome APIs, so it runs in the popup, the service worker,
// and Node tests alike.

export const HOURS_PER_MANDAY = 8;
const MILLIS_PER_HOUR = 3_600_000;

/** A participant's cost per hour, normalising man-day rates to an hourly figure. */
export function hourlyRate(attendee) {
  const value = Number(attendee.rateValue) || 0;
  return attendee.rateType === "MANDAY" ? value / HOURS_PER_MANDAY : value;
}

/** Combined per-hour cost of the whole roster (negative/zero rates ignored). */
export function perHourTotal(attendees) {
  return attendees.reduce((sum, a) => sum + Math.max(0, hourlyRate(a)), 0);
}

/** Cost accrued after `elapsedMillis` given a per-hour rate. */
export function costAtElapsed(perHour, elapsedMillis) {
  return perHour * (elapsedMillis / MILLIS_PER_HOUR);
}

/** Formats an amount with the given ISO currency, falling back to USD if invalid. */
export function formatMoney(amount, currencyCode, locale) {
  const code = (currencyCode || "USD").toUpperCase();
  try {
    return new Intl.NumberFormat(locale, { style: "currency", currency: code }).format(amount);
  } catch {
    return new Intl.NumberFormat(locale, { style: "currency", currency: "USD" }).format(amount);
  }
}

/**
 * How many whole `threshold` steps the cost has reached (threshold 100 -> 1 at
 * 100, 2 at 200, ...). Returns 0 when the reminder is disabled.
 */
export function reminderStep(cost, threshold) {
  if (!threshold || threshold <= 0) return 0;
  return Math.floor(cost / threshold);
}

/**
 * Live elapsed milliseconds for a stored meeting, derived from timestamps so it
 * stays accurate while the popup/service worker are asleep.
 */
export function elapsedMillis(meeting, now = Date.now()) {
  if (!meeting) return 0;
  const base = meeting.baseElapsedMs || 0;
  return meeting.running ? base + (now - meeting.lastResumeTs) : base;
}
