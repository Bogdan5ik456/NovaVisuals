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
  try { r = await fetch(siteUrl() + '/api/' + route, { method, headers, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(25000) }); }
  catch { throw new Error('Сайт недоступний за адресою ' + siteUrl() + '. Перевір адресу в Налаштуваннях (Адреса сайту Nova Visuals) і інтернет; безкоштовний хостинг може прокидатися до хвилини.'); }
  let j = null;
  try { j = JSON.parse(await r.text()); } catch {}
  if (!r.ok || !j) {
    const e = new Error((j && j.error) || 'Сайт відповів не так, як очікується (HTTP ' + r.status + '). Адреса сайту в Налаштуваннях має вести на запущений server/ (не на сторінку GitHub і не на закритий порт).');
    e.status = r.status;
    throw e;
  }
  return j;
}

let presenceTimer = null;

function status() {
  try { return readToken() && fs.existsSync(infoFile()) ? { ...JSON.parse(fs.readFileSync(infoFile(), 'utf8')), url: siteUrl() } : null; } catch { return null; }
}

// Відкриває сайт: там ти входиш і тиснеш «Отримати код», а потім вписуєш його в лаунчері
async function openSite() {
  const pong = await call('GET', 'ping');   // перевіряємо, що за цією адресою саме наш сайт
  if (!pong || pong.app !== 'nova-visuals') throw new Error('За адресою ' + siteUrl() + ' відповів не сайт Nova Visuals. Перевір адресу в Налаштуваннях.');
  shell.openExternal(siteUrl());
  return { url: siteUrl() };
}

// Обмін коду з сайту на токен лаунчера
async function redeem(code) {
  const c = String(code || '').trim();
  if (!c) throw new Error('Впиши код із сайту');
  const n = c.replace(/[^0-9a-fA-F]/g, '').length;
  if (n !== 10) throw new Error('Код із сайту має 10 символів (формат XXXXX-XXXXX), а ти ввів ' + n + '. Натисни на код на сайті й скопіюй його повністю.');
  const p = await call('POST', 'link/redeem', { code: c });
  saveToken(p.token);
  fs.writeFileSync(infoFile(), JSON.stringify({ name: p.name }));
  startPresence();
  return { name: p.name };
}
function stopPolling() { /* лишилось для сумісності */ }

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

module.exports = { status, openSite, redeem, stopPolling, logout, friends, friendAction, writeLink, startPresence, siteUrl, setSiteUrl };
