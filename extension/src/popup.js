import * as store from "./store.js";
import { perHourTotal, costAtElapsed, formatMoney, elapsedMillis, reminderStep } from "./cost.js";
import { buildMeeting, pauseToggle, endMeeting } from "./meeting.js";

const locale = chrome.i18n.getUILanguage();
const t = (key) => chrome.i18n.getMessage(key) || key;
const $ = (id) => document.getElementById(id);

let attendees = [];
let currency = "USD";
let threshold = 0;
let tick = null;

// ---------- i18n ----------
function applyI18n() {
  document.querySelectorAll("[data-i18n]").forEach((el) => {
    el.textContent = t(el.dataset.i18n);
  });
  document.querySelectorAll("[data-i18n-ph]").forEach((el) => {
    el.placeholder = t(el.dataset.i18nPh);
  });
  document.querySelectorAll("[data-i18n-title]").forEach((el) => {
    el.title = t(el.dataset.i18nTitle);
  });
}

// ---------- view switching ----------
function show(view) {
  for (const v of ["rosterView", "meetingView", "historyView"]) $(v).hidden = v !== view;
}

// ---------- formatting ----------
function fmtElapsed(ms) {
  const s = Math.floor(ms / 1000);
  const hh = String(Math.floor(s / 3600)).padStart(2, "0");
  const mm = String(Math.floor((s % 3600) / 60)).padStart(2, "0");
  const ss = String(s % 60).padStart(2, "0");
  return `${hh}:${mm}:${ss}`;
}
function fmtDateTime(ms) {
  return new Date(ms).toLocaleString(locale, { dateStyle: "medium", timeStyle: "short" });
}

// ---------- roster ----------
function renderRoster() {
  const list = $("attendeeList");
  list.innerHTML = "";
  $("rosterEmpty").hidden = attendees.length > 0;
  for (const a of attendees) {
    const li = document.createElement("li");
    li.className = "item";
    const unit = a.rateType === "MANDAY" ? t("rateManday") : t("rateHourly");
    li.innerHTML = `
      <div class="grow">
        <div class="name"></div>
        <div class="sub"></div>
      </div>
      <div class="rate"></div>
      <button class="icon-btn" title="Delete">✕</button>`;
    li.querySelector(".name").textContent = a.name;
    li.querySelector(".sub").textContent = a.email || "";
    li.querySelector(".rate").textContent = `${formatMoney(Number(a.rateValue) || 0, currency, locale)} / ${unit}`;
    li.querySelector("button").addEventListener("click", () => removeAttendee(a.id));
    list.appendChild(li);
  }
  const perHour = perHourTotal(attendees);
  $("burnRate").textContent = `${formatMoney(perHour, currency, locale)} / h`;
  $("startBtn").disabled = perHour <= 0;
}

async function addAttendee(e) {
  e.preventDefault();
  const name = $("aName").value.trim();
  const rate = parseFloat($("aRate").value.replace(",", "."));
  if (!name || !(rate > 0)) return;
  attendees.push({
    id: store.newId(),
    name,
    email: $("aEmail").value.trim(),
    rateType: $("aType").value,
    rateValue: rate,
  });
  await store.saveAttendees(attendees);
  $("addForm").reset();
  renderRoster();
}

async function removeAttendee(id) {
  attendees = attendees.filter((a) => a.id !== id);
  await store.saveAttendees(attendees);
  renderRoster();
}

// ---------- meeting ----------
async function startMeeting() {
  const m = buildMeeting(attendees, currency || "USD", threshold);
  await store.setMeeting(m);
  enterMeeting();
}

function enterMeeting() {
  show("meetingView");
  startTicking();
}

function startTicking() {
  stopTicking();
  renderMeeting();
  tick = setInterval(renderMeeting, 1000);
}
function stopTicking() {
  if (tick) clearInterval(tick);
  tick = null;
}

async function renderMeeting() {
  const m = await store.getMeeting();
  if (!m) {
    stopTicking();
    show("rosterView");
    return;
  }
  const ended = m.phase === "ENDED";
  const ms = elapsedMillis(m);
  const cost = costAtElapsed(m.perHour, ms);

  $("meetingTitle").textContent = ended ? t("resultTitle") : t("meetingInProgress");
  $("mAttendees").textContent = `${m.attendeeCount} ${t("attendees")}`;
  const rem = $("mReminder");
  rem.hidden = !(m.threshold > 0);
  if (m.threshold > 0) rem.textContent = `${t("buzzesEvery")} ${formatMoney(m.threshold, m.currency, locale)}`;
  $("mCost").textContent = formatMoney(cost, m.currency, locale);
  $("mElapsedLabel").textContent = ended ? t("duration") : t("elapsed");
  $("mElapsed").textContent = fmtElapsed(ms);
  $("pauseBtn").querySelector("span").textContent = m.running ? t("pause") : t("resume");
  $("pauseBtn").firstChild.textContent = m.running ? "⏸ " : "▶ ";

  $("runningControls").hidden = ended;
  $("endedControls").hidden = !ended;
  if (ended) stopTicking();
}

async function togglePause() {
  const m = pauseToggle(await store.getMeeting());
  await store.setMeeting(m);
  renderMeeting();
}

async function endCurrent() {
  const m = await store.getMeeting();
  if (!m) return;
  const { meeting, record } = endMeeting(m, Date.now(), locale);
  await store.setMeeting(meeting);
  await store.addHistory(record);
  renderMeeting();
}

async function emailAttendees() {
  const m = await store.getMeeting();
  if (!m) return;
  if (!m.recipients.length) {
    alert(t("noRecipients"));
    return;
  }
  const ms = elapsedMillis(m);
  const money = formatMoney(costAtElapsed(m.perHour, ms), m.currency, locale);
  const date = new Date(m.startedAt).toLocaleDateString(locale, { dateStyle: "medium" });
  const subject = t("emailSubject").replace("{money}", money).replace("{date}", date);
  const body = t("emailBody").replace("{money}", money).replace("{date}", date);
  const url = `mailto:${encodeURIComponent(m.recipients.join(","))}?subject=${encodeURIComponent(
    subject,
  )}&body=${encodeURIComponent(body)}`;
  chrome.tabs.create({ url });
}

async function doneMeeting() {
  await store.setMeeting(null);
  show("rosterView");
  renderRoster();
}

// ---------- history ----------
async function openHistory() {
  const { history } = await store.get(["history"]);
  const list = $("historyList");
  list.innerHTML = "";
  $("historyEmpty").hidden = history.length > 0;
  for (const r of history) {
    const li = document.createElement("li");
    li.className = "item";
    li.innerHTML = `<div class="grow"><div class="name"></div><div class="sub"></div></div><div class="rate"></div>`;
    li.querySelector(".name").textContent = fmtDateTime(r.startedAt);
    li.querySelector(".sub").textContent = `${fmtElapsed(r.durationMillis)} · ${r.attendeeCount} ${t("attendees")}`;
    li.querySelector(".rate").textContent = r.costSummary;
    list.appendChild(li);
  }
  show("historyView");
}

async function clearHistory() {
  await store.set({ history: [] });
  openHistory();
}

// ---------- init ----------
async function init() {
  applyI18n();
  const state = await store.get();
  attendees = state.attendees;
  currency = state.currency || "USD";
  threshold = state.threshold || 0;

  $("currency").value = currency;
  $("threshold").value = threshold > 0 ? String(threshold) : "";

  $("addForm").addEventListener("submit", addAttendee);
  $("currency").addEventListener("change", async () => {
    currency = ($("currency").value.trim().toUpperCase() || "USD");
    $("currency").value = currency;
    await store.set({ currency });
    renderRoster();
  });
  $("threshold").addEventListener("change", async () => {
    threshold = parseFloat($("threshold").value.replace(",", ".")) || 0;
    await store.set({ threshold });
  });
  $("startBtn").addEventListener("click", startMeeting);
  $("pauseBtn").addEventListener("click", togglePause);
  $("endBtn").addEventListener("click", endCurrent);
  $("emailBtn").addEventListener("click", emailAttendees);
  $("doneBtn").addEventListener("click", doneMeeting);
  $("openHistory").addEventListener("click", openHistory);
  $("historyBack").addEventListener("click", () => { show("rosterView"); renderRoster(); });
  $("clearHistory").addEventListener("click", clearHistory);

  renderRoster();

  // Resume into the live view if a meeting is already running.
  const meeting = await store.getMeeting();
  if (meeting) enterMeeting();
}

init();
