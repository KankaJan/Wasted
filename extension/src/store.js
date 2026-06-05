// Thin wrapper over chrome.storage.local — the web equivalent of Room + SettingsStore.
//
// Values are encrypted at rest with AES-GCM (WebCrypto) before being written, and
// decrypted on read. NOTE: an extension has no hardware keystore, so the key is
// stored in chrome.storage.local alongside the data. This protects against casual
// inspection of the profile's LevelDB on disk and other extensions/tools reading
// raw storage, but it is not a defense against code already running as this
// extension. (Documented limitation accepted for the threat model.)

const DEFAULTS = {
  attendees: [], // [{ id, name, email, rateType: 'HOURLY'|'MANDAY', rateValue }]
  currency: "", // empty -> resolved to the locale currency in the UI
  threshold: 0, // 0 -> reminder off
  meeting: null, // active meeting state, see startMeeting()
  history: [], // [{ startedAt, durationMillis, attendeeCount, costSummary }]
};

// Storage key that holds the raw AES key itself (never encrypted, never exposed
// through get()/set()).
const ENC_KEY_NAME = "__enc_key_v1";

// ---------- crypto helpers ----------
function bytesToBase64(bytes) {
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin);
}
function base64ToBytes(b64) {
  const bin = atob(b64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

let keyPromise = null;
function getKey() {
  if (keyPromise) return keyPromise;
  keyPromise = (async () => {
    const stored = await chrome.storage.local.get(ENC_KEY_NAME);
    if (stored[ENC_KEY_NAME]) {
      return crypto.subtle.importKey(
        "raw",
        base64ToBytes(stored[ENC_KEY_NAME]),
        "AES-GCM",
        false,
        ["encrypt", "decrypt"],
      );
    }
    const key = await crypto.subtle.generateKey({ name: "AES-GCM", length: 256 }, true, [
      "encrypt",
      "decrypt",
    ]);
    const raw = new Uint8Array(await crypto.subtle.exportKey("raw", key));
    await chrome.storage.local.set({ [ENC_KEY_NAME]: bytesToBase64(raw) });
    // Guard against a first-run race: if another context (popup vs. service
    // worker) generated and stored a key first, adopt theirs so both sides use
    // the same key and can decrypt each other's writes.
    const after = await chrome.storage.local.get(ENC_KEY_NAME);
    if (after[ENC_KEY_NAME] && after[ENC_KEY_NAME] !== bytesToBase64(raw)) {
      return crypto.subtle.importKey(
        "raw",
        base64ToBytes(after[ENC_KEY_NAME]),
        "AES-GCM",
        false,
        ["encrypt", "decrypt"],
      );
    }
    return key;
  })();
  return keyPromise;
}

function isEnvelope(v) {
  return v != null && typeof v === "object" && v.__enc === 1;
}

async function encrypt(value) {
  const key = await getKey();
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const data = new TextEncoder().encode(JSON.stringify(value ?? null));
  const ct = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, key, data));
  return { __enc: 1, iv: bytesToBase64(iv), ct: bytesToBase64(ct) };
}

async function decrypt(envelope) {
  const key = await getKey();
  const pt = await crypto.subtle.decrypt(
    { name: "AES-GCM", iv: base64ToBytes(envelope.iv) },
    key,
    base64ToBytes(envelope.ct),
  );
  return JSON.parse(new TextDecoder().decode(pt));
}

// ---------- storage API ----------
export async function get(keys) {
  const wanted = keys || Object.keys(DEFAULTS);
  const stored = await chrome.storage.local.get(wanted);
  const out = {};
  for (const k of wanted) {
    const raw = stored[k];
    if (raw === undefined) out[k] = DEFAULTS[k];
    else if (isEnvelope(raw)) out[k] = await decrypt(raw);
    // Tolerate values written before encryption was introduced; they get
    // re-encrypted on the next write.
    else out[k] = raw;
  }
  return out;
}

export async function set(values) {
  const encrypted = {};
  for (const [k, v] of Object.entries(values)) encrypted[k] = await encrypt(v);
  await chrome.storage.local.set(encrypted);
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
