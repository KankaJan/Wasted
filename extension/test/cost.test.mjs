import { test } from "node:test";
import assert from "node:assert/strict";
import {
  hourlyRate,
  perHourTotal,
  costAtElapsed,
  reminderStep,
  formatMoney,
  elapsedMillis,
} from "../src/cost.js";

test("man-day rate is normalised to 8 hours", () => {
  assert.equal(hourlyRate({ rateType: "MANDAY", rateValue: 800 }), 100);
  assert.equal(hourlyRate({ rateType: "HOURLY", rateValue: 50 }), 50);
});

test("perHourTotal sums the roster and ignores non-positive rates", () => {
  const total = perHourTotal([
    { rateType: "HOURLY", rateValue: 50 },
    { rateType: "MANDAY", rateValue: 800 }, // 100/h
    { rateType: "HOURLY", rateValue: 0 },
  ]);
  assert.equal(total, 150);
});

test("cost at half an hour is half the hourly rate", () => {
  assert.equal(costAtElapsed(120, 1_800_000), 60);
});

test("reminderStep counts whole thresholds, 0 when disabled", () => {
  assert.equal(reminderStep(99, 100), 0);
  assert.equal(reminderStep(100, 100), 1);
  assert.equal(reminderStep(350, 100), 3);
  assert.equal(reminderStep(999, 0), 0);
});

test("formatMoney uses the given currency and falls back to USD", () => {
  assert.equal(formatMoney(1234.5, "USD", "en-US"), "$1,234.50");
  assert.equal(formatMoney(10, "NOTACODE", "en-US"), "$10.00");
});

test("elapsedMillis derives from timestamps and respects pause", () => {
  const now = 10_000_000;
  const running = { running: true, baseElapsedMs: 5000, lastResumeTs: now - 2000 };
  assert.equal(elapsedMillis(running, now), 7000);
  const paused = { running: false, baseElapsedMs: 5000, lastResumeTs: now - 2000 };
  assert.equal(elapsedMillis(paused, now), 5000);
});
