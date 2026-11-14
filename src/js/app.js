// Nova Visuals — логіка інтерфейсу (етап 1: дизайн і налаштування)
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  // ---------- Збереження ----------
  const load = (k, d) => { try { return { ...d, ...JSON.parse(localStorage.getItem(k)) }; } catch { return d; } };
  const save = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} };
  const settings = load('nova.settings', { lang: 'uk', ram: 4096, res: '1280x720', autoUpdate: false, fpsBoost: true, noNova: false, closeOnLaunch: false, account: 'offline', requireSite: false });
  const api = window.nova;
  const profile = load('nova.profile', { nick: 'Player' });
  const choice = load('nova.choice', { version: '1.18.2', loader: 'fabric' });
  choice.loader = 'fabric';
  let skin = null; try { skin = localStorage.getItem('nova.skin'); } catch {}

  const t = (k) => (I18N[settings.lang] && I18N[settings.lang][k]) || I18N.uk[k] || k;

  // ---------- Дані версій ----------
  // ok = доступно; check = потрібно підтвердити на етапі 2 через офіційні джерела
  const VERSIONS = { '1.21.11': { fabric: 'ok' }, '1.18.2': { fabric: 'ok' } };
  const LOADERS = { fabric: 'Fabric + Nova Visuals' };

  // ---------- Іконки та переклад ----------
  function paintIcons() { $$('[data-icon]').forEach(el => { el.innerHTML = icon(el.dataset.icon); }); }
  function applyLang() {
    document.documentElement.lang = settings.lang;
    $$('[data-i18n]').forEach(el => { el.textContent = t(el.dataset.i18n); });
    renderPlay(); renderMods(); renderSummary(); paintNick(); paintMs(); paintAcc(); paintSite(); $('#fr-name').placeholder = t('fr.placeholder'); $('#lib-q').placeholder = t('lib.placeholder');
  }

  // ---------- Тост ----------
  let toastTimer;
  function toast(msg) {
    const el = $('#toast'); el.textContent = msg; el.classList.add('show');
    clearTimeout(toastTimer); toastTimer = setTimeout(() => el.classList.remove('show'), 2600);
  }

  // ---------- Навігація ----------
  function go(page) {
    $$('.page').forEach(p => p.classList.toggle('active', p.id === 'page-' + page));
    $$('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.page === page));
    $('.content').scrollTop = 0;
    if (page === 'mods') renderMods();
    if (page === 'friends') loadFriends();
  }
  $$('.nav-item').forEach(b => b.addEventListener('click', () => go(b.dataset.page)));
  $$('[data-goto]').forEach(b => b.addEventListener('click', () => go(b.dataset.goto)));

  // ---------- Керування вікном ----------
  if (window.nova) {
    $('#btn-min').addEventListener('click', () => nova.minimize());
    $('#btn-max').addEventListener('click', () => nova.maximize());
    $('#btn-close').addEventListener('click', () => nova.close());
  }

  // ---------- Грати ----------
  function renderPlay() {
    const vc = $('#version-cards'); vc.innerHTML = '';
    Object.keys(VERSIONS).forEach(v => {
      const b = document.createElement('button');
      b.className = 'vcard' + (v === choice.version ? ' selected' : '');
      b.innerHTML = `<b>Minecraft ${v}</b><small>Java Edition</small>`;
      b.addEventListener('click', () => { choice.version = v; save('nova.choice', choice); renderPlay(); renderSummary(); renderMods(); });
      vc.appendChild(b);
    });
    const lc = $('#loader-chips'); lc.innerHTML = '';
    Object.entries(LOADERS).forEach(([key, name]) => {
      const b = document.createElement('button');
      b.className = 'chip' + (key === choice.loader ? ' active' : '');
      b.textContent = name;
      b.addEventListener('click', () => { choice.loader = key; save('nova.choice', choice); renderPlay(); renderSummary(); });
      lc.appendChild(b);
    });
    const status = VERSIONS[choice.version][choice.loader];
    $('#loader-note').textContent = t(choice.version === '1.18.2' ? 'loader.fabric' : 'loader.fabric121');
  }
  function renderSummary() {
    const txt = `Minecraft ${choice.version} · ${LOADERS[choice.loader]} · ${settings.ram} MB`;
    $('#play-summary').textContent = txt; $('#home-summary').textContent = txt;
    $('#hero-title').textContent = `Minecraft ${choice.version}`;
    $('#hero-sub').textContent = `${LOADERS[choice.loader]} · ${t('home.sub')}`;
  }
  let launching = false, logLines = [];
  function setProgress(text, pct) {
    $('#progress').classList.remove('hidden'); $('#progress-text').textContent = text; $('#bar-fill').style.width = pct + '%';
  }
  function addLog(text) {
    logLines.push(text); if (logLines.length > 200) logLines.shift();
    const el = $('#log'); el.textContent = logLines.join('\n'); el.scrollTop = el.scrollHeight;
  }
  if (api) api.onGameEvent(ev => {
    if (ev.type === 'progress') setProgress(ev.text, ev.percent || 0);
    else if (ev.type === 'log') addLog(ev.text);
    else if (ev.type === 'started') { setProgress(t('msg.playing'), 100); toast(t('msg.playing')); }
    else if (ev.type === 'closed') { launching = false; $('#btn-launch').disabled = false; toast(t('msg.closed')); }
  });
  $('#btn-launch').addEventListener('click', async () => {
    if (!api || launching) return;
    if (VERSIONS[choice.version][choice.loader] === 'soon') { toast(t('loader.soon')); return; }
    if (settings.requireSite && !siteProfile) { toast(t('site.need')); go('profile'); return; }
    if (settings.account === 'microsoft' && !msProfile) { toast(t('msg.msneed')); go('profile'); return; }
    launching = true; $('#btn-launch').disabled = true; logLines = []; $('#log').textContent = ''; setProgress('…', 0);
    const r = await api.launch({ version: choice.version, loader: choice.loader, ram: settings.ram, res: settings.res,
      authType: settings.account, nick: profile.nick, fpsBoost: settings.fpsBoost, noNova: settings.noNova, closeOnLaunch: settings.closeOnLaunch });
    if (!r.ok) { launching = false; $('#btn-launch').disabled = false; setProgress(r.error, 0); addLog(r.error); toast(r.error); }
  });
  $('#btn-log').addEventListener('click', () => api && api.openLog());
  $('#btn-folder').addEventListener('click', () => api && api.openFolder(choice.version));
  $('#mods-folder').addEventListener('click', () => api && api.openFolder(choice.version));


  // ---------- Моди / ресурс-паки / бібліотека ----------
  let modsTab = 'mods', modsView = 'installed', libBusy = false;
  const fmtNum = n => n >= 1e6 ? (n / 1e6).toFixed(1) + 'M' : n >= 1e3 ? Math.round(n / 1e3) + 'K' : String(n);
  async function renderMods() {
    $$('#mods-tabs .chip').forEach(c => c.classList.toggle('active', c.dataset.tab === modsTab));
    $$('#mods-view .chip').forEach(c => c.classList.toggle('active', c.dataset.view === modsView));
    $('#view-installed').classList.toggle('hidden', modsView !== 'installed');
    $('#view-lib').classList.toggle('hidden', modsView !== 'lib');
    $('#mods-empty-title').textContent = t(modsTab === 'mods' ? 'mods.emptyMods' : 'mods.emptyPacks');
    $('#mods-version').textContent = t('mods.version') + choice.version;
    if (modsView === 'lib') return;
    const list = $('#files-list'); list.innerHTML = '';
    let items = []; if (api) { const r = await api.listFiles(choice.version, modsTab); items = r.ok ? r.data : []; }
    $('#mods-empty').classList.toggle('hidden', items.length > 0);
    items.forEach(it => {
      const li = document.createElement('li'); if (!it.enabled) li.className = 'off';
      const name = document.createElement('span'); name.textContent = it.name; li.appendChild(name);
      const acts = document.createElement('div'); acts.className = 'acts';
      if (it.system) {   // файли, якими керує лаунчер: видалити чи вимкнути не можна
        const tag = document.createElement('span'); tag.className = 'sys'; tag.textContent = t('mods.system'); acts.appendChild(tag);
      } else {
        if (modsTab === 'mods') { const b = document.createElement('button'); b.className = 'btn secondary'; b.textContent = t('btn.toggle');
          b.addEventListener('click', async () => { await api.toggleMod(choice.version, it.name); renderMods(); }); acts.appendChild(b); }
        const d = document.createElement('button'); d.className = 'btn secondary'; d.textContent = t('btn.remove');
        d.addEventListener('click', async () => { const r = await api.removeFile(choice.version, modsTab, it.name); if (!r.ok) toast(r.error); renderMods(); }); acts.appendChild(d);
      }
      li.appendChild(acts); list.appendChild(li);
    });
  }
  async function libSearch() {
    if (!api || libBusy) return;
    libBusy = true;
    $('#lib-status').textContent = t('lib.loading'); $('#lib-list').innerHTML = '';
    const r = await api.libSearch(modsTab, $('#lib-q').value.trim(), choice.version, 0);
    libBusy = false;
    if (!r.ok) { $('#lib-status').textContent = r.error; return; }
    $('#lib-status').textContent = r.data.length ? '' : t('lib.none');
    r.data.forEach(h => {
      const li = document.createElement('li'); li.className = 'libitem';
      const img = document.createElement('img'); img.width = 40; img.height = 40; img.alt = ''; if (h.icon) img.src = h.icon; li.appendChild(img);
      const mid = document.createElement('div'); mid.className = 'libtxt';
      const b = document.createElement('b'); b.textContent = h.title; mid.appendChild(b);
      const sm = document.createElement('small'); sm.textContent = `${h.author} · ${fmtNum(h.downloads)}`; mid.appendChild(sm);
      const p = document.createElement('p'); p.textContent = h.desc; mid.appendChild(p);
      li.appendChild(mid);
      const btn = document.createElement('button'); btn.className = 'btn primary'; btn.textContent = t('lib.install');
      btn.addEventListener('click', async () => {
        btn.disabled = true; btn.textContent = t('lib.installing');
        const x = await api.libInstall(modsTab, h.id, choice.version);
        if (x.ok) { btn.textContent = t('lib.installed'); toast(t('lib.done') + x.data.join(', ')); }
        else { btn.disabled = false; btn.textContent = t('lib.install'); toast(x.error); }
      });
      li.appendChild(btn); $('#lib-list').appendChild(li);
    });
  }
  $('#lib-go').addEventListener('click', libSearch);
  $('#lib-q').addEventListener('keydown', e => { if (e.key === 'Enter') libSearch(); });
  $$('#mods-view .chip').forEach(c => c.addEventListener('click', () => { modsView = c.dataset.view; renderMods(); if (modsView === 'lib' && !$('#lib-list').children.length) libSearch(); }));
  $$('#mods-tabs .chip').forEach(c => c.addEventListener('click', () => { modsTab = c.dataset.tab; $('#lib-list').innerHTML = ''; renderMods(); if (modsView === 'lib') libSearch(); }));
  $('#mods-add').addEventListener('click', async () => { if (!api) return; await api.addFiles(choice.version, modsTab); renderMods(); });

  // ---------- Налаштування ----------
  const ram = $('#ram');
  function paintRam() { ram.style.setProperty('--fill', ((ram.value - 1024) / (8192 - 1024) * 100) + '%'); $('#ram-val').textContent = ram.value + ' MB'; }
  ram.value = settings.ram; paintRam();
  ram.addEventListener('input', () => { settings.ram = +ram.value; paintRam(); save('nova.settings', settings); renderSummary(); });
  $('#res').value = settings.res;
  $('#res').addEventListener('change', e => { settings.res = e.target.value; save('nova.settings', settings); toast(t('msg.saved')); });
  $$('input[name=lang]').forEach(r => { r.checked = r.value === settings.lang;
    r.addEventListener('change', () => { settings.lang = r.value; save('nova.settings', settings); applyLang(); toast(t('msg.saved')); }); });
  $('#auto-update').checked = settings.autoUpdate;
  $('#auto-update').addEventListener('change', e => { settings.autoUpdate = e.target.checked; save('nova.settings', settings); });
  $('#fps-boost').checked = settings.fpsBoost;
  $('#fps-boost').addEventListener('change', e => { settings.fpsBoost = e.target.checked; save('nova.settings', settings); });
  $('#no-nova').checked = settings.noNova;
  $('#no-nova').addEventListener('change', e => { settings.noNova = e.target.checked; save('nova.settings', settings); });
  $('#close-on-launch').checked = settings.closeOnLaunch;
  $('#close-on-launch').addEventListener('change', e => { settings.closeOnLaunch = e.target.checked; save('nova.settings', settings); });

  // ---------- Профіль ----------
  const nick = $('#nick'); nick.value = profile.nick;
  let msProfile = null;
  function paintNick() {
    const ms = settings.account === 'microsoft' && msProfile;
    $('#side-nick').textContent = ms ? msProfile.name : profile.nick;
    $('#side-mode').textContent = ms ? 'Microsoft' : t('user.offline');
  }
  function paintMs() { $('#ms-status').textContent = msProfile ? t('prof.msin') + msProfile.name : t('prof.msout'); $('#ms-logout').disabled = !msProfile; }
  function paintAcc() { $$('#acc-tabs .chip').forEach(c => c.classList.toggle('active', c.dataset.acc === settings.account)); paintNick(); }
  async function refreshMs() { if (!api) return; const r = await api.msStatus(); msProfile = r.ok ? r.data : null; paintMs(); paintNick(); }
  $$('#acc-tabs .chip').forEach(c => c.addEventListener('click', () => {
    if (c.dataset.acc === 'microsoft' && !msProfile) { toast(t('msg.msneed')); return; }
    settings.account = c.dataset.acc; save('nova.settings', settings); paintAcc(); }));
  $('#ms-login').addEventListener('click', async () => {
    if (!api) return; const r = await api.msLogin();
    if (!r.ok) { toast(r.error); return; }
    settings.account = 'microsoft'; save('nova.settings', settings); await refreshMs(); paintAcc(); toast(t('msg.msok'));
  });
  $('#ms-logout').addEventListener('click', async () => {
    if (!api) return; await api.msLogout(); msProfile = null; settings.account = 'offline'; save('nova.settings', settings); paintMs(); paintAcc(); toast(t('msg.msout'));
  });
  $('#nick-save').addEventListener('click', () => {
    const v = nick.value.trim();
    if (!/^[A-Za-z0-9_]{3,16}$/.test(v)) { $('#nick-err').textContent = t('msg.nick'); return; }
    $('#nick-err').textContent = ''; profile.nick = v; save('nova.profile', profile); paintNick(); toast(t('msg.saved'));
  });

  // ---------- Скіни: редактор ----------
  const ed = $('#skin-edit'), ex = ed.getContext('2d');
  let tool = 'pen', painting = false;
  const REGIONS = { head: [[0, 0, 32, 16]], body: [[16, 16, 24, 16]], arms: [[40, 16, 16, 16], [32, 48, 16, 16]], legs: [[0, 16, 16, 16], [16, 48, 16, 16]] };

  function defaultSkin() {   // власний стандартний скін Nova
    const R = (x, y, w, h, c) => { ex.fillStyle = c; ex.fillRect(x, y, w, h); };
    ex.clearRect(0, 0, 64, 64);
    R(0, 0, 32, 16, '#c99a72');                 // голова
    R(8, 0, 16, 8, '#3b2a5c'); R(8, 8, 8, 3, '#3b2a5c'); R(0, 8, 8, 4, '#3b2a5c'); R(16, 8, 8, 4, '#3b2a5c'); R(24, 8, 8, 4, '#3b2a5c');   // волосся
    R(9, 12, 2, 1, '#ffffff'); R(13, 12, 2, 1, '#ffffff'); R(10, 12, 1, 1, '#7a2cf7'); R(14, 12, 1, 1, '#7a2cf7'); R(11, 14, 2, 1, '#8a4b3b');   // обличчя
    R(16, 16, 24, 16, '#7a2cf7');               // тіло
    R(40, 16, 16, 16, '#7a2cf7'); R(40, 28, 16, 4, '#c99a72');   // права рука
    R(32, 48, 16, 16, '#7a2cf7'); R(32, 60, 16, 4, '#c99a72');   // ліва рука
    R(0, 16, 16, 16, '#2a2350'); R(16, 48, 16, 16, '#2a2350');   // ноги
    refreshPreviews();
  }
  function refreshPreviews() {
    const head = $('#skin-head').getContext('2d'), full = $('#skin-full').getContext('2d');
    [head, full].forEach(c => { c.imageSmoothingEnabled = false; c.clearRect(0, 0, c.canvas.width, c.canvas.height); });
    head.drawImage(ed, 8, 8, 8, 8, 0, 0, 8, 8); head.drawImage(ed, 40, 8, 8, 8, 0, 0, 8, 8);
    full.drawImage(ed, 0, 0);
  }
  function paintSide() {
    const c = $('#side-avatar').getContext('2d'); c.imageSmoothingEnabled = false; c.clearRect(0, 0, 8, 8);
    c.drawImage(ed, 8, 8, 8, 8, 0, 0, 8, 8); c.drawImage(ed, 40, 8, 8, 8, 0, 0, 8, 8);
  }
  function loadSkinData(dataUrl, then) {
    const img = new Image();
    img.onload = () => { ex.clearRect(0, 0, 64, 64); ex.imageSmoothingEnabled = false; ex.drawImage(img, 0, 0); refreshPreviews(); if (then) then(); };
    img.src = dataUrl;
  }
  function pixelAt(e) {
    const r = ed.getBoundingClientRect();
    return [Math.floor((e.clientX - r.left) / r.width * 64), Math.floor((e.clientY - r.top) / r.height * 64)];
  }
  function paintPixel(e) {
    const [x, y] = pixelAt(e); if (x < 0 || y < 0 || x > 63 || y > 63) return;
    if (tool === 'eraser') ex.clearRect(x, y, 1, 1); else { ex.fillStyle = $('#skin-color').value; ex.fillRect(x, y, 1, 1); }
    refreshPreviews();
  }
  ed.addEventListener('pointerdown', e => { painting = true; ed.setPointerCapture(e.pointerId); paintPixel(e); });
  ed.addEventListener('pointermove', e => { if (painting) paintPixel(e); });
  ed.addEventListener('pointerup', () => { painting = false; });
  $('#tool-pen').addEventListener('click', () => { tool = 'pen'; $('#tool-pen').classList.add('active'); $('#tool-eraser').classList.remove('active'); });
  $('#tool-eraser').addEventListener('click', () => { tool = 'eraser'; $('#tool-eraser').classList.add('active'); $('#tool-pen').classList.remove('active'); });
  $('#part-fill').addEventListener('click', () => {
    ex.fillStyle = $('#skin-color').value;
    REGIONS[$('#part-sel').value].forEach(([x, y, w, h]) => ex.fillRect(x, y, w, h));
    refreshPreviews();
  });
  $('#skin-default').addEventListener('click', defaultSkin);
  $('#skin-file').addEventListener('change', e => {
    const file = e.target.files[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        if (img.width !== 64 || img.height !== 64) { toast(t('msg.skinBad')); return; }
        loadSkinData(reader.result, () => toast(t('msg.skinLoaded')));
      };
      img.onerror = () => toast(t('msg.skinBad'));
      img.src = reader.result;
    };
    reader.readAsDataURL(file); e.target.value = '';
  });
  $('#skin-save').addEventListener('click', async () => {
    const url = ed.toDataURL('image/png');
    if (api) { const r = await api.setSkin(url); if (!r.ok) { toast(r.error); return; } }
    skin = url; try { localStorage.setItem('nova.skin', skin); } catch {}
    paintSide(); toast(t('msg.skinOk'));
  });
  $('#skin-remove').addEventListener('click', () => {
    skin = null; try { localStorage.removeItem('nova.skin'); } catch {} if (api) api.clearSkin();
    defaultSkin(); paintSide(); toast(t('msg.skinGone'));
  });
  function initSkinEditor() { if (skin) loadSkinData(skin, paintSide); else defaultSkin(); }

  // ---------- Оновлення ----------
  if (api) {
    api.onUpdate(ev => {
      const bar = $('#update-bar');
      if (ev.state === 'downloading') { bar.textContent = t('upd.downloading') + (ev.version ? ' v' + ev.version : '') + (ev.percent ? ' — ' + ev.percent + '%' : ''); bar.classList.remove('hidden'); }
      else if (ev.state === 'ready') { bar.textContent = t('upd.ready') + ' v' + ev.version; bar.classList.remove('hidden'); }
      else if (ev.state === 'none') toast(t('upd.none'));
    });
    api.appInfo().then(r => { if (r.ok) $('#app-ver').textContent = t('set.version') + r.data.version; });
    const paintMod = async () => {
      const r = await api.modStatus(choice.version); if (!r.ok) return;
      $('#mod-status').textContent = r.data.installed ? t('mod.st.ok') : (r.data.bundled ? t('mod.st.no') : t('mod.st.nobundle'));
    };
    paintMod();
    $('#btn-mod').addEventListener('click', async () => {
      const r = await api.installMod(choice.version);
      if (!r.ok) toast(r.error); else toast(t('mod.st.ok'));
      paintMod();
    });
    $('#btn-update').addEventListener('click', async () => { const r = await api.checkUpdates(true); if (!r.ok) toast(t('upd.err')); });
    if (settings.autoUpdate) api.checkUpdates(false);   // тихо: без інтернету просто нічого не станеться
  }

  // ---------- Сайт Nova Visuals: вхід, друзі ----------
  let siteProfile = null, siteWaiting = '', friendsTimer = null;
  function paintSite() {
    $('#site-status').textContent = siteWaiting ? t('site.waiting') + siteWaiting : (siteProfile ? t('site.in') + siteProfile.name : t('site.out'));
    $('#site-login').classList.toggle('hidden', !!siteProfile || !!siteWaiting);
    $('#site-cancel').classList.toggle('hidden', !siteWaiting);
    $('#site-logout').classList.toggle('hidden', !siteProfile);
  }
  async function refreshSite() { if (!api) return; const r = await api.siteStatus(); siteProfile = r.ok ? r.data : null; paintSite(); }
  function frRow(name, sub, on, buttons) {
    const li = document.createElement('li'); li.className = 'friend';
    const left = document.createElement('div'); left.className = 'frow';
    const dot = document.createElement('span'); dot.className = 'dot' + (on ? ' on' : ''); left.appendChild(dot);
    const txt = document.createElement('div'); const b = document.createElement('b'); b.textContent = name; txt.appendChild(b);
    if (sub) { const sm = document.createElement('small'); sm.textContent = sub; txt.appendChild(sm); }
    left.appendChild(txt); li.appendChild(left);
    const acts = document.createElement('div'); acts.className = 'acts';
    buttons.forEach(([label, fn]) => { const x = document.createElement('button'); x.className = 'btn secondary'; x.textContent = label; x.addEventListener('click', fn); acts.appendChild(x); });
    li.appendChild(acts); return li;
  }
  const ago = ms => { const m = Math.round((Date.now() - ms) / 60000); return !ms ? '' : m < 1 ? ' · <1m' : m < 60 ? ' · ' + m + 'm' : m < 1440 ? ' · ' + Math.round(m / 60) + 'h' : ' · ' + Math.round(m / 1440) + 'd'; };
  async function friendDo(action, name) {
    const r = await api.siteFriend(action, name);
    if (!r.ok) { toast(r.error); return null; }
    loadFriends(); return r.data;
  }
  async function loadFriends() {
    if (!api) return;
    $('#fr-login').classList.toggle('hidden', !!siteProfile);
    $('#fr-main').classList.toggle('hidden', !siteProfile);
    clearTimeout(friendsTimer);
    if (!siteProfile) return;
    const r = await api.siteFriends();
    if (!r.ok) { $('#fr-err').textContent = r.error; refreshSite(); return; }
    const f = r.data, list = $('#fr-list'), inc = $('#fr-in'), out = $('#fr-out');
    list.innerHTML = inc.innerHTML = out.innerHTML = '';
    f.friends.forEach(u => list.appendChild(frRow(u.name,
      u.online ? (u.inGame ? t('fr.ingame') + (u.server ? ' · ' + u.server : '') : t('fr.online')) : t('fr.offline') + ago(u.lastSeen), u.online,
      [[t('fr.remove'), () => friendDo('remove', u.name)]])));
    f.incoming.forEach(n => inc.appendChild(frRow(n, t('fr.wants'), false,
      [[t('fr.accept'), () => friendDo('accept', n)], [t('fr.decline'), () => friendDo('decline', n)]])));
    f.outgoing.forEach(n => out.appendChild(frRow(n, t('fr.waiting'), false, [])));
    $('#fr-empty').classList.toggle('hidden', f.friends.length > 0);
    $('#fr-in-h').classList.toggle('hidden', !f.incoming.length);
    $('#fr-out-h').classList.toggle('hidden', !f.outgoing.length);
    if ($('#page-friends').classList.contains('active')) friendsTimer = setTimeout(loadFriends, 15000);
  }
  $('#fr-add').addEventListener('click', async () => {
    const v = $('#fr-name').value.trim(); $('#fr-err').textContent = '';
    if (!/^[A-Za-z0-9_]{3,16}$/.test(v)) { $('#fr-err').textContent = t('msg.nick'); return; }
    const d = await friendDo('add', v);
    if (d) { $('#fr-name').value = ''; toast(d.status === 'friends' ? t('fr.now') : t('fr.sent')); }
  });
  $('#fr-name').addEventListener('keydown', e => { if (e.key === 'Enter') $('#fr-add').click(); });
  $('#site-login').addEventListener('click', async () => {
    if (!api) return;
    const r = await api.siteStart();
    if (!r.ok) { toast(r.error); return; }
    siteWaiting = r.data.code; paintSite();
  });
  $('#site-cancel').addEventListener('click', async () => { if (api) await api.siteCancel(); siteWaiting = ''; paintSite(); });
  $('#site-logout').addEventListener('click', async () => { if (api) await api.siteLogout(); siteProfile = null; paintSite(); toast(t('msg.saved')); });
  if (api) api.onSite(ev => {
    siteWaiting = '';
    if (ev.state === 'ok') { siteProfile = { name: ev.name }; toast(t('site.ok')); } else toast(t('site.expired'));
    paintSite();
  });
  $('#require-site').checked = settings.requireSite;
  $('#require-site').addEventListener('change', e => { settings.requireSite = e.target.checked; save('nova.settings', settings); });
  if (api) api.siteGetUrl().then(r => { if (r.ok) $('#site-url').value = r.data; });
  $('#site-url-save').addEventListener('click', async () => {
    if (!api) return; const r = await api.siteSetUrl($('#site-url').value);
    if (!r.ok) { toast(r.error); return; } $('#site-url').value = r.data; toast(t('msg.saved'));
  });

  // ---------- Старт ----------
  paintIcons(); applyLang(); initSkinEditor(); refreshMs(); refreshSite();
})();
