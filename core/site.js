// Зв'язок із сайтом Nova Visuals: вхід через сайт (підтвердження лаунчера), друзі, присутність
const { app, safeStorage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const paths = require('./paths');

// Адреса сайту (Vercel). Можна змінити в Налаштуваннях лаунчера
const DEFAULT_SITE = 'https://nova-visuals-client.vercel.app';

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

// Ключі Supabase лежать у core/supabase.json (url і публічний anon-ключ, його не треба ховати)
function sbCfg() {
  let c = {};
  try { c = JSON.parse(fs.readFileSync(path.join(__dirname, 'supabase.json'), 'utf8')); } catch {}
  const url = String(c.url || '').trim().replace(/\/+$/, ''), key = String(c.key || '').trim();
  if (!/^https:\/\/[^\s/]+\.supabase\.co$/i.test(url) || key.length < 20)
    throw new Error('У файлі core/supabase.json не вписані url і key від Supabase. Візьми їх у Vercel: Settings → Environment Variables (VITE_SUPABASE_URL і VITE_SUPABASE_ANON_KEY).');
  return { url, key };
}

async function rpc(fn, body) {
  const { url, key } = sbCfg();
  const headers = { 'Content-Type': 'application/json', apikey: key };
  if (key.startsWith('eyJ')) headers.Authorization = 'Bearer ' + key;
  let r;
  try { r = await fetch(url + '/rest/v1/rpc/' + fn, { method: 'POST', headers, body: JSON.stringify(body || {}), signal: AbortSignal.timeout(25000) }); }
  catch { throw new Error('Немає зв\'язку з базою сайту. Перевір інтернет.'); }
  let j = null;
  try { j = JSON.parse(await r.text()); } catch {}
  if (!r.ok) {
    const m = String((j && (j.message || j.error)) || '');
    if (/invalid or expired code/i.test(m)) { const e = new Error('Код недійсний або прострочений. Отримай новий на сайті.'); e.status = 400; throw e; }
    if (/schema cache|does not exist|Could not find/i.test(m)) throw new Error('База сайту не налаштована: виконай supabase/setup_all.sql у Supabase (SQL Editor).');
    if (r.status === 401 || r.status === 403) throw new Error('Supabase не прийняв ключ. Перевір key у core/supabase.json.');
    throw new Error('Помилка сайту (HTTP ' + r.status + '): ' + (m || 'невідома'));
  }
  return j;
}

let presenceTimer = null;

function status() {
  try { return readToken() && fs.existsSync(infoFile()) ? { ...JSON.parse(fs.readFileSync(infoFile(), 'utf8')), url: siteUrl() } : null; } catch { return null; }
}

// Відкриває сайт на сторінці акаунта: там входиш і тиснеш «Отримати код підтвердження», потім вписуєш код у лаунчері
async function openSite() {
  sbCfg();   // одразу покажемо зрозумілу помилку, якщо ключі не вписані
  const u = siteUrl() + '/account';
  shell.openExternal(u);
  return { url: u };
}

// Обмін коду з сайту на токен лаунчера
async function redeem(code) {
  const c = String(code || '').trim().toUpperCase().replace(/[\s-]/g, '');
  if (!c) throw new Error('Впиши код із сайту');
  if (!/^[0-9A-F]{8}$/.test(c)) throw new Error('Код із сайту має 8 символів (літери A–F і цифри), а ти ввів ' + c.length + '. Скопіюй його повністю.');
  const rows = await rpc('confirm_install', { p_code: c, p_version: app.getVersion() });
  const p = Array.isArray(rows) ? rows[0] : rows;
  if (!p || !p.client_token) throw new Error('Сайт не повернув токен. Спробуй отримати новий код.');
  const name = p.nick || 'гравець';
  saveToken(p.client_token);
  fs.writeFileSync(infoFile(), JSON.stringify({ name }));
  startPresence();
  return { name };
}
function stopPolling() { /* лишилось для сумісності */ }

function logout() {
  stopPolling();
  if (presenceTimer) { clearInterval(presenceTimer); presenceTimer = null; }
  [tokenFile(), infoFile()].forEach(f => fs.rmSync(f, { force: true }));
  for (const v of ['1.18.2', '1.21.11']) fs.rmSync(linkPath(v), { force: true });   // мод більше не бачить токена
}

// Статус онлайн/офлайн на сайті поки не ведеться, тож це заглушка
function startPresence() {}

async function friends() {
  const t = readToken();
  if (!t) throw new Error('Спочатку увійди через сайт на сторінці «Профіль».');
  const rows = await rpc('client_friends', { p_token: t });
  const list = (Array.isArray(rows) ? rows : []).map(r => ({ name: r.nick, online: false, inGame: false, lastSeen: 0 }));
  list.sort((x, y) => String(x.name).localeCompare(String(y.name)));
  return { friends: list, incoming: [], outgoing: [] };
}
const friendAction = () => { throw new Error('Друзів додають і видаляють на сайті: сторінка «Акаунт», розділ «Друзі».'); };

// Файл, з якого мод у грі бере адресу сайту й токен (щоб показувати друзів онлайн/офлайн)
const linkPath = (version) => path.join(paths.instance(version), 'config', 'novavisuals-link.json');
function writeLink(version) {
  fs.rmSync(linkPath(version), { force: true });   // мод поки що не вміє працювати з сайтом на Supabase
  return false;
}

module.exports = { status, openSite, redeem, stopPolling, logout, friends, friendAction, writeLink, startPresence, siteUrl, setSiteUrl };
