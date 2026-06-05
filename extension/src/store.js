// Thin wrapper over chrome.storage.local — the web equivalent of Room + SettingsStore.

const DEFAULTS = {
  attendees: [], // [{ id, name, email, rateType: 'HOURLY'|'MANDAY', rateValue }]
  currency: "", // empty -> resolved to the locale currency in the UI
  threshold: 0, // 0 -> reminder off
  meeting: null, // active meeting state, see startMeeting()
  history: [], // [{ startedAt, durationMillis, attendeeCount, costSummary }]
};

export async function get(keys) {
  const wanted = keys || Object.keys(DEFAULTS);
  const stored = await chrome.storage.local.get(wanted);
  const out = {};
  for (const k of wanted) out[k] = stored[k] !== undefined ? stored[k] : DEFAULTS[k];
  return out;
}

export async function set(values) {
  await chrome.storage.local.set(values);
}

export async function getAttendees() {
  return (await get(["attendees"])).attendees;
}

export async function saveAttendees(attendees) {
  await set({ attendees });
}

export async function getSettings() {
  const { currency, threshold } = await get(["currency", "threshold"]);
  return { currency, threshold };
}

export async function getMeeting() {
  return (await get(["meeting"])).meeting;
}

export async function setMeeting(meeting) {
  await set({ meeting });
}

export async function addHistory(record) {
  const { history } = await get(["history"]);
  history.unshift(record);
  await set({ history: history.slice(0, 100) });
}

export function newId() {
  return Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
}
