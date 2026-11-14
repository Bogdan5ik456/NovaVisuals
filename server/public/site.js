(() => {
  const $ = s => document.querySelector(s);
  let mode = 'login', token = null, linkCode = '';
  try { token = localStorage.getItem('nv.token'); } catch {}
  const params = new URLSearchParams(location.search);
  linkCode = (params.get('code') || '').toUpperCase().replace(/[^0-9A-F-]/g, '').slice(0, 9);

  async function api(path, body) {
    const opt = { headers: { 'Content-Type': 'application/json' } };
    if (token) opt.headers.Authorization = 'Bearer ' + token;
    if (body !== undefined) { opt.method = 'POST'; opt.body = JSON.stringify(body); }
    const r = await fetch('/api/' + path, opt);
    let j = {}; try { j = await r.json(); } catch {}
    if (!r.ok) throw Object.assign(new Error(j.error || 'Помилка'), { status: r.status });
    return j;
  }
  const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };
  const ago = ms => { const m = Math.round((Date.now() - ms) / 60000); return !ms ? 'давно' : m < 1 ? 'щойно' : m < 60 ? m + ' хв тому' : m < 1440 ? Math.round(m / 60) + ' год тому' : Math.round(m / 1440) + ' дн тому'; };

  document.querySelectorAll('.tab').forEach(b => b.addEventListener('click', () => {
    mode = b.dataset.m;
    document.querySelectorAll('.tab').forEach(x => x.classList.toggle('active', x === b));
    $('#go').textContent = mode === 'login' ? 'Увійти' : 'Створити акаунт';
    $('#pw').autocomplete = mode === 'login' ? 'current-password' : 'new-password';
    $('#aerr').textContent = '';
  }));
  async function doAuth() {
    $('#aerr').textContent = '';
    try {
      const r = await api(mode, { name: $('#name').value.trim(), password: $('#pw').value });
      token = r.token; try { localStorage.setItem('nv.token', token); } catch {}
      $('#pw').value = ''; start();
    } catch (e) { $('#aerr').textContent = e.message; }
  }
  $('#go').addEventListener('click', doAuth);
  $('#pw').addEventListener('keydown', e => { if (e.key === 'Enter') doAuth(); });

  function show(authed) { $('#auth').classList.toggle('hidden', authed); $('#app').classList.toggle('hidden', !authed); }
  function row(name, sub, on) {
    const li = el('li'); const d = el('span', 'dot' + (on ? ' on' : '')); li.appendChild(d);
    const t = el('div', 'nm'); t.appendChild(el('b', '', name)); if (sub) t.appendChild(el('small', '', sub)); li.appendChild(t);
    return li;
  }
  async function loadFriends() {
    try {
      const f = await api('friends');
      const fl = $('#friends'), il = $('#incoming'), ol = $('#outgoing');
      fl.innerHTML = il.innerHTML = ol.innerHTML = '';
      f.friends.forEach(u => {
        const li = row(u.name, u.online ? (u.inGame ? 'У грі' + (u.mc ? ' як ' + u.mc : '') + (u.server ? ' · ' + u.server : '') : 'У лаунчері') : 'Офлайн · ' + ago(u.lastSeen), u.online);
        const b = el('button', 'sec', 'Видалити'); b.addEventListener('click', async () => { await api('friends/remove', { name: u.name }); loadFriends(); }); li.appendChild(b);
        fl.appendChild(li);
      });
      f.incoming.forEach(n => {
        const li = row(n, 'хоче додати тебе', false);
        const a = el('button', '', 'Прийняти'); a.addEventListener('click', async () => { await api('friends/accept', { name: n }); loadFriends(); });
        const d = el('button', 'sec', 'Відхилити'); d.addEventListener('click', async () => { await api('friends/decline', { name: n }); loadFriends(); });
        li.append(a, d); il.appendChild(li);
      });
      f.outgoing.forEach(n => ol.appendChild(row(n, 'очікує відповіді', false)));
      $('#fempty').classList.toggle('hidden', f.friends.length > 0);
      $('#inh').classList.toggle('hidden', !f.incoming.length);
      $('#outh').classList.toggle('hidden', !f.outgoing.length);
    } catch (e) { if (e.status === 401) logoutLocal(); }
  }
  $('#fadd').addEventListener('click', async () => {
    $('#ferr').textContent = '';
    try {
      const r = await api('friends/add', { name: $('#fname').value.trim() });
      $('#fname').value = ''; $('#ferr').textContent = r.status === 'friends' ? 'Ви тепер друзі' : 'Запит надіслано'; $('#ferr').className = 'err ok'; loadFriends();
    } catch (e) { $('#ferr').className = 'err'; $('#ferr').textContent = e.message; }
  });

  async function confirm(code, errEl) {
    errEl.textContent = '';
    try { await api('device/confirm', { code }); errEl.className = 'err ok'; errEl.textContent = 'Готово! Повернись у лаунчер: він увійде автоматично.'; $('#linkbox').classList.add('hidden'); }
    catch (e) { errEl.className = 'err'; errEl.textContent = e.message; }
  }
  $('#confirm').addEventListener('click', () => confirm(linkCode, $('#lerr')));
  $('#cancel-link').addEventListener('click', () => { linkCode = ''; $('#linkbox').classList.add('hidden'); history.replaceState(null, '', '/'); });
  $('#manual-go').addEventListener('click', () => confirm($('#manual').value.trim(), $('#merr')));

  function logoutLocal() { token = null; try { localStorage.removeItem('nv.token'); } catch {} $('#who').textContent = ''; show(false); }
  $('#logout').addEventListener('click', async () => { try { await api('logout', {}); } catch {} logoutLocal(); });
  $('#logout-all').addEventListener('click', async () => { try { await api('logout-all', {}); } catch {} logoutLocal(); });

  let timer = null;
  async function start() {
    if (!token) return show(false);
    try {
      const me = await api('me');
      if (me.kind !== 'web') return logoutLocal();
      $('#who').textContent = me.name; show(true);
      if (linkCode) {
        const i = await fetch('/api/device/info?code=' + encodeURIComponent(linkCode)).then(r => r.json()).catch(() => ({}));
        if (i.valid) { $('#linkcode').textContent = linkCode; $('#linkbox').classList.remove('hidden'); }
        else { $('#lerr').textContent = 'Код недійсний або застарів. Запусти вхід у лаунчері ще раз.'; $('#linkbox').classList.remove('hidden'); $('#linkcode').textContent = linkCode; }
      }
      loadFriends(); clearInterval(timer); timer = setInterval(() => { if (!document.hidden) loadFriends(); }, 15000);
    } catch { logoutLocal(); }
  }
  start();
})();
