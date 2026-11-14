// Nova Visuals — сайт і API (без залежностей, потрібен лише Node 18+)
// Запуск: node server.js   (змінні: PORT, DATA_FILE, PUBLIC_URL)
'use strict';
const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = +process.env.PORT || 3000;
const DATA_FILE = process.env.DATA_FILE || path.join(__dirname, 'data.json');
const PUBLIC_DIR = path.join(__dirname, 'public');
const ONLINE_MS = 70 * 1000, GAME_MS = 40 * 1000;
const NAME_RE = /^[A-Za-z0-9_]{3,16}$/;

// ---------- Сховище (JSON-файл, запис із затримкою) ----------
let db = { users: {}, tokens: {} };   // users[id] ; tokens[sha256] = { uid, kind, exp }
try { db = { ...db, ...JSON.parse(fs.readFileSync(DATA_FILE, 'utf8')) }; } catch {}
let saveTimer = null;
function saveSoon() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try { fs.writeFileSync(DATA_FILE + '.tmp', JSON.stringify(db)); fs.renameSync(DATA_FILE + '.tmp', DATA_FILE); } catch (e) { console.error('save', e.message); }
  }, 400);
}
const devices = new Map();   // code -> { deviceToken, uid|null, exp, issued }   (в пам'яті, живуть 10 хв)

const sha = (s) => crypto.createHash('sha256').update(s).digest('hex');
const rnd = (n) => crypto.randomBytes(n).toString('hex');
const now = () => Date.now();
const userByName = (n) => Object.values(db.users).find(u => u.name.toLowerCase() === String(n).toLowerCase());

function hashPw(pw, salt = rnd(16)) { return salt + ':' + crypto.scryptSync(pw, salt, 64).toString('hex'); }
function checkPw(pw, stored) {
  const [salt, h] = stored.split(':');
  const a = Buffer.from(h, 'hex'), b = crypto.scryptSync(pw, salt, 64);
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}
function issue(uid, kind, days) {
  const token = rnd(32);
  db.tokens[sha(token)] = { uid, kind, exp: now() + days * 864e5 };
  saveSoon();
  return token;
}
function authOf(req) {
  const h = req.headers.authorization || '';
  if (!h.startsWith('Bearer ')) return null;
  const t = db.tokens[sha(h.slice(7))];
  if (!t || t.exp < now() || !db.users[t.uid]) return null;
  return { user: db.users[t.uid], kind: t.kind };
}

// ---------- Обмеження частоти ----------
const hits = new Map();
function limited(req, key, max, windowMs) {
  const k = key + ':' + (req.socket.remoteAddress || '');
  const t = now(), arr = (hits.get(k) || []).filter(x => t - x < windowMs);
  arr.push(t); hits.set(k, arr);
  return arr.length > max;
}
setInterval(() => { const t = now(); for (const [k, a] of hits) if (!a.some(x => t - x < 3600e3)) hits.delete(k); for (const [c, d] of devices) if (d.exp < t) devices.delete(c); }, 60e3).unref();

// ---------- Допоміжне ----------
function send(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body);
}
function readBody(req) {
  return new Promise((resolve, reject) => {
    let n = 0; const chunks = [];
    req.on('data', c => { n += c.length; if (n > 10_000) { reject(new Error('big')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => { try { resolve(chunks.length ? JSON.parse(Buffer.concat(chunks).toString()) : {}); } catch { reject(new Error('json')); } });
  });
}
const clean = (s, max = 64) => String(s == null ? '' : s).replace(/[\u0000-\u001f]/g, '').slice(0, max);

function presenceOf(u) {
  const p = u.pres || {}, t = now();
  const inGame = p.gameTs && t - p.gameTs < GAME_MS;
  const online = inGame || (p.appTs && t - p.appTs < ONLINE_MS);
  return { online: !!online, inGame: !!inGame, mc: inGame ? p.mc || '' : '', server: inGame ? p.server || '' : '', lastSeen: Math.max(p.gameTs || 0, p.appTs || 0) };
}
function friendView(me) {
  const friends = (me.friends || []).map(id => db.users[id]).filter(Boolean).map(u => ({ name: u.name, ...presenceOf(u) }));
  friends.sort((a, b) => (b.online - a.online) || a.name.localeCompare(b.name));
  const incoming = (me.incoming || []).map(id => db.users[id]).filter(Boolean).map(u => u.name);
  const outgoing = (me.outgoing || []).map(id => db.users[id]).filter(Boolean).map(u => u.name);
  return { friends, incoming, outgoing };
}
const pull = (arr, id) => { const i = arr.indexOf(id); if (i >= 0) arr.splice(i, 1); };
function makeFriends(a, b) {
  for (const [x, y] of [[a, b], [b, a]]) {
    x.friends = x.friends || []; if (!x.friends.includes(y.id)) x.friends.push(y.id);
    pull(x.incoming = x.incoming || [], y.id); pull(x.outgoing = x.outgoing || [], y.id);
  }
}

// ---------- API ----------
const routes = {
  'POST /api/register': async (req, res, body) => {
    if (limited(req, 'reg', 8, 3600e3)) return send(res, 429, { error: 'Забагато спроб. Спробуй пізніше.' });
    const name = clean(body.name, 16), pw = String(body.password || '');
    if (!NAME_RE.test(name)) return send(res, 400, { error: 'Нік: 3-16 символів, латиниця, цифри, _' });
    if (pw.length < 8 || pw.length > 100) return send(res, 400, { error: 'Пароль: від 8 символів' });
    if (userByName(name)) return send(res, 409, { error: 'Цей нік уже зайнятий' });
    const id = rnd(8);
    db.users[id] = { id, name, pass: hashPw(pw), created: now(), friends: [], incoming: [], outgoing: [] };
    saveSoon();
    send(res, 200, { token: issue(id, 'web', 30), name });
  },
  'POST /api/login': async (req, res, body) => {
    if (limited(req, 'login', 15, 600e3)) return send(res, 429, { error: 'Забагато спроб. Зачекай кілька хвилин.' });
    const u = userByName(clean(body.name, 16));
    if (!u || !checkPw(String(body.password || ''), u.pass)) return send(res, 401, { error: 'Невірний нік або пароль' });
    send(res, 200, { token: issue(u.id, 'web', 30), name: u.name });
  },
  'GET /api/me': async (req, res) => {
    const a = authOf(req); if (!a) return send(res, 401, { error: 'Увійди' });
    send(res, 200, { name: a.user.name, kind: a.kind });
  },
  'POST /api/logout': async (req, res) => {
    const h = (req.headers.authorization || '').slice(7);
    if (h) { delete db.tokens[sha(h)]; saveSoon(); }
    send(res, 200, { ok: true });
  },
  'POST /api/logout-all': async (req, res) => {   // скасувати всі лаунчери й сесії цього акаунта
    const a = authOf(req); if (!a || a.kind !== 'web') return send(res, 401, { error: 'Увійди на сайті' });
    for (const [k, t] of Object.entries(db.tokens)) if (t.uid === a.user.id) delete db.tokens[k];
    saveSoon(); send(res, 200, { ok: true });
  },

  // --- Підтвердження лаунчера через сайт ---
  'POST /api/device/start': async (req, res) => {
    if (limited(req, 'dev', 20, 600e3)) return send(res, 429, { error: 'Забагато запитів' });
    const code = (rnd(3) + '').toUpperCase().slice(0, 4) + '-' + rnd(3).toUpperCase().slice(0, 4);
    const deviceToken = rnd(24);
    devices.set(code, { deviceToken, uid: null, exp: now() + 10 * 60e3 });
    send(res, 200, { code, deviceToken, expiresIn: 600 });
  },
  'GET /api/device/poll': async (req, res, _b, url) => {
    const dt = url.searchParams.get('deviceToken') || '';
    for (const [code, d] of devices) {
      if (d.deviceToken !== dt) continue;
      if (d.exp < now()) { devices.delete(code); return send(res, 200, { status: 'expired' }); }
      if (!d.uid) return send(res, 200, { status: 'pending' });
      devices.delete(code);
      const u = db.users[d.uid];
      return send(res, 200, { status: 'ok', token: issue(u.id, 'launcher', 90), name: u.name });
    }
    send(res, 200, { status: 'expired' });
  },
  'GET /api/device/info': async (req, res, _b, url) => {
    const d = devices.get(clean(url.searchParams.get('code'), 12).toUpperCase());
    send(res, 200, { valid: !!(d && d.exp > now() && !d.uid) });
  },
  'POST /api/device/confirm': async (req, res, body) => {
    const a = authOf(req); if (!a || a.kind !== 'web') return send(res, 401, { error: 'Спочатку увійди на сайті' });
    if (limited(req, 'confirm', 15, 600e3)) return send(res, 429, { error: 'Забагато спроб' });
    const d = devices.get(clean(body.code, 12).toUpperCase());
    if (!d || d.exp < now() || d.uid) return send(res, 404, { error: 'Код недійсний або застарів. Запусти вхід у лаунчері ще раз.' });
    d.uid = a.user.id;
    send(res, 200, { ok: true });
  },

  // --- Друзі ---
  'GET /api/friends': async (req, res) => {
    const a = authOf(req); if (!a) return send(res, 401, { error: 'Увійди' });
    send(res, 200, friendView(a.user));
  },
  'POST /api/friends/add': async (req, res, body) => {
    const a = authOf(req); if (!a) return send(res, 401, { error: 'Увійди' });
    if (limited(req, 'fadd', 30, 600e3)) return send(res, 429, { error: 'Забагато запитів' });
    const t = userByName(clean(body.name, 16));
    if (!t || t.id === a.user.id) return send(res, 404, { error: 'Користувача не знайдено' });
    const me = a.user;
    me.friends = me.friends || []; me.incoming = me.incoming || []; me.outgoing = me.outgoing || [];
    t.incoming = t.incoming || []; t.outgoing = t.outgoing || [];
    if (me.friends.includes(t.id)) return send(res, 200, { status: 'friends' });
    if (me.incoming.includes(t.id)) { makeFriends(me, t); saveSoon(); return send(res, 200, { status: 'friends' }); }
    if (!me.outgoing.includes(t.id)) {
      if (me.outgoing.length >= 50) return send(res, 400, { error: 'Забагато вихідних запитів' });
      me.outgoing.push(t.id); t.incoming.push(me.id);
    }
    saveSoon(); send(res, 200, { status: 'sent' });
  },
  'POST /api/friends/accept': async (req, res, body) => {
    const a = authOf(req); if (!a) return send(res, 401, { error: 'Увійди' });
    const t = userByName(clean(body.name, 16));
    if (!t || !(a.user.incoming || []).includes(t.id)) return send(res, 404, { error: 'Запиту немає' });
    makeFriends(a.user, t); saveSoon(); send(res, 200, { ok: true });
  },
  'POST /api/friends/decline': async (req, res, body) => {
    const a = authOf(req); if (!a) return send(res, 401, { error: 'Увійди' });
    const t = userByName(clean(body.name, 16));
    if (t) { pull(a.user.incoming || [], t.id); pull(t.outgoing || [], a.user.id); saveSoon(); }
    send(res, 200, { ok: true });
  },
  'POST /api/friends/remove': async (req, res, body) => {
    const a = authOf(req); if (!a) return send(res, 401, { error: 'Увійди' });
    const t = userByName(clean(body.name, 16));
    if (t) { pull(a.user.friends || [], t.id); pull(t.friends || [], a.user.id); pull(a.user.outgoing || [], t.id); pull(t.incoming || [], a.user.id); saveSoon(); }
    send(res, 200, { ok: true });
  },

  // --- Присутність: лаунчер (state=app) і мод у грі (state=game) ---
  'POST /api/presence': async (req, res, body) => {
    const a = authOf(req); if (!a) return send(res, 401, { error: 'Увійди' });
    const p = a.user.pres = a.user.pres || {}, t = now();
    if (body.state === 'game') { p.gameTs = t; p.appTs = t; p.mc = clean(body.mc, 16); p.server = clean(body.server, 48); }
    else if (body.state === 'leave') { p.gameTs = 0; p.mc = ''; p.server = ''; p.appTs = t; }
    else p.appTs = t;
    send(res, 200, { ok: true });   // присутність у файл не пишемо щоразу: вона тимчасова
  }
};

// ---------- Статика ----------
const MIME = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'text/javascript', '.svg': 'image/svg+xml', '.png': 'image/png', '.ico': 'image/x-icon' };
function serveStatic(req, res, pathname) {
  let rel = pathname === '/' || pathname === '/link' ? '/index.html' : pathname;
  const file = path.normalize(path.join(PUBLIC_DIR, rel));
  if (!file.startsWith(PUBLIC_DIR + path.sep)) { res.writeHead(403); return res.end(); }
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); return res.end('Not found'); }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file)] || 'application/octet-stream',
      'Content-Security-Policy': "default-src 'self'; style-src 'self' 'unsafe-inline'; script-src 'self'; img-src 'self' data:; frame-ancestors 'none'",
      'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer'
    });
    res.end(data);
  });
}

http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    if (url.pathname.startsWith('/api/')) {
      const fn = routes[req.method + ' ' + url.pathname];
      if (!fn) return send(res, 404, { error: 'Не знайдено' });
      const body = req.method === 'POST' ? await readBody(req) : {};
      return await fn(req, res, body, url);
    }
    if (req.method !== 'GET') { res.writeHead(405); return res.end(); }
    serveStatic(req, res, url.pathname);
  } catch (e) {
    if (!res.headersSent) send(res, e.message === 'big' ? 413 : 400, { error: 'Некоректний запит' });
  }
}).listen(PORT, () => console.log('Nova Visuals site: http://localhost:' + PORT));
