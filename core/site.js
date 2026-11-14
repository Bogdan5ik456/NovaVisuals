// Зв'язок із сайтом Nova Visuals: вхід через сайт (підтвердження лаунчера), друзі, присутність
const { app, safeStorage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const paths = require('./paths');

// Адресу свого сайту (server/) пропиши тут або зміни в Налаштуваннях лаунчера
const DEFAULT_SITE = 'http://localhost:3000';

const tokenFile = () => path.join(app.getPath('userData'), 'site-token.bin');
const infoFile = () => path.join(app.getPath('userData'), 'site-profile.json');
const cfgFile = () => path.join(app.getPath('userData'), 'site-config.json');

function siteUrl() {
  try { const u = JSON.parse(fs.readFileSync(cfgFile(), 'utf8')).url; if (u) return u; } catch {}
  return DEFAULT_SITE;
}
function setSiteUrl(url) {
  url = String(url || '').trim().replace(/\/+$/, '');
  if (!/^https?:\/\/[^\s/]+(:\d+)?$/i.test(url)) throw new Error('Адреса має вигляд https://novavisuals.example');
  fs.writeFileSync(cfgFile(), JSON.stringify({ url }));
  return url;
}

function saveToken(t) { fs.writeFileSync(tokenFile(), safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(t) : Buffer.from(t)); }
function readToken() {
  try {
    if (!fs.existsSync(tokenFile())) return null;
    const b = fs.readFileSync(tokenFile());
    return safeStorage.isEncryptionAvailable() ? safeStorage.decryptString(b) : b.toString();
  } catch { return null; }
}

async function call(method, route, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  let r;
  try { r = await fetch(siteUrl() + '/api/' + route, { method, headers, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(8000) }); }
  catch { throw new Error('Сайт недоступний. Перевір адресу сайту в Налаштуваннях і інтернет.'); }
  let j = {}; try { j = await r.json(); } catch {}
  if (!r.ok) { const e = new Error(j.error || 'Помилка сайту'); e.status = r.status; throw e; }
  return j;
}

let pollTimer = null, presenceTimer = null;

function status() {
  try { return readToken() && fs.existsSync(infoFile()) ? { ...JSON.parse(fs.readFileSync(infoFile(), 'utf8')), url: siteUrl() } : null; } catch { return null; }
}

// Крок 1: просимо код, відкриваємо сайт. Крок 2: опитуємо, доки ти не підтвердиш на сайті.
async function start(onEvent) {
  stopPolling();
  const d = await call('POST', 'device/start');
  const url = siteUrl() + '/link?code=' + encodeURIComponent(d.code);
  shell.openExternal(url);
  const until = Date.now() + d.expiresIn * 1000;
  pollTimer = setInterval(async () => {
    try {
      if (Date.now() > until) { stopPolling(); onEvent({ state: 'expired' }); return; }
      const p = await call('GET', 'device/poll?deviceToken=' + encodeURIComponent(d.deviceToken));
      if (p.status === 'ok') {
        stopPolling(); saveToken(p.token); fs.writeFileSync(infoFile(), JSON.stringify({ name: p.name }));
        startPresence(); onEvent({ state: 'ok', name: p.name });
      } else if (p.status === 'expired') { stopPolling(); onEvent({ state: 'expired' }); }
    } catch { /* тимчасова мережева помилка: пробуємо далі */ }
  }, 2000);
  return { code: d.code, url };
}
function stopPolling() { if (pollTimer) { clearInterval(pollTimer); pollTimer = null; } }

function logout() {
  stopPolling();
  if (presenceTimer) { clearInterval(presenceTimer); presenceTimer = null; }
  const t = readToken();
  if (t) call('POST', 'logout', {}, t).catch(() => {});
  [tokenFile(), infoFile()].forEach(f => fs.rmSync(f, { force: true }));
  for (const v of ['1.18.2', '1.21.11']) fs.rmSync(linkPath(v), { force: true });   // мод більше не бачить токена
}

function startPresence() {
  if (presenceTimer) return;
  const beat = () => { const t = readToken(); if (t) call('POST', 'presence', { state: 'app' }, t).catch(() => {}); };
  beat(); presenceTimer = setInterval(beat, 30000);
}

async function authed(method, route, body) {
  const t = readToken();
  if (!t) throw new Error('Спочатку увійди через сайт на сторінці «Профіль».');
  try { return await call(method, route, body, t); }
  catch (e) { if (e.status === 401) logout(); throw e; }
}
const friends = () => authed('GET', 'friends');
const friendAction = (action, name) => {
  if (!['add', 'accept', 'decline', 'remove'].includes(action)) throw new Error('Невірна дія');
  return authed('POST', 'friends/' + action, { name: String(name || '').slice(0, 16) });
};

// Файл, з якого мод у грі бере адресу сайту й токен (щоб показувати друзів онлайн/офлайн)
const linkPath = (version) => path.join(paths.instance(version), 'config', 'novavisuals-link.json');
function writeLink(version) {
  const t = readToken(), p = status();
  if (!t || !p) { fs.rmSync(linkPath(version), { force: true }); return false; }
  fs.mkdirSync(path.dirname(linkPath(version)), { recursive: true });
  fs.writeFileSync(linkPath(version), JSON.stringify({ api: siteUrl(), token: t, user: p.name }), { mode: 0o600 });
  return true;
}

module.exports = { status, start, stopPolling, logout, friends, friendAction, writeLink, startPresence, siteUrl, setSiteUrl };
