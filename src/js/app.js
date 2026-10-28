// Nova Visuals — логіка інтерфейсу (етап 1: дизайн і налаштування)
(() => {
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  // ---------- Збереження ----------
  const load = (k, d) => { try { return { ...d, ...JSON.parse(localStorage.getItem(k)) }; } catch { return d; } };
  const save = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch {} };
  const settings = load('nova.settings', { lang: 'uk', ram: 4096, res: '1280x720', autoUpdate: false, fpsBoost: true, closeOnLaunch: false, account: 'offline' });
  const api = window.nova;
  const profile = load('nova.profile', { nick: 'Player' });
  const choice = load('nova.choice', { version: '1.21.11', loader: 'vanilla' });
  let skin = null; try { skin = localStorage.getItem('nova.skin'); } catch {}

  const t = (k) => (I18N[settings.lang] && I18N[settings.lang][k]) || I18N.uk[k] || k;

  // ---------- Дані версій ----------
  // ok = доступно; check = потрібно підтвердити на етапі 2 через офіційні джерела
  const VERSIONS = {
    '1.21.11': { vanilla: 'ok', forge: 'ok', optifine: 'check', forgeoptifine: 'check', nova: 'soon' },
    '1.18.2':  { vanilla: 'ok', forge: 'ok',    optifine: 'ok',    forgeoptifine: 'ok', nova: 'ok' }
  };
  const LOADERS = { vanilla: 'Vanilla', forge: 'Forge', optifine: 'OptiFine', forgeoptifine: 'Forge + OptiFine', nova: 'Nova Visuals' };

  // ---------- Іконки та переклад ----------
  function paintIcons() { $$('[data-icon]').forEach(el => { el.innerHTML = icon(el.dataset.icon); }); }
  function applyLang() {
    document.documentElement.lang = settings.lang;
    $$('[data-i18n]').forEach(el => { el.textContent = t(el.dataset.i18n); });
    renderPlay(); renderMods(); renderSummary(); paintNick(); paintMs(); paintAcc();
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
    refreshOptifine();
    $('#loader-note').textContent = choice.loader === 'vanilla' ? t('loader.vanilla') : choice.loader === 'nova' ? t(status === 'ok' ? 'loader.nova' : 'loader.soon') : (status === 'ok' ? t('loader.ok') : t('loader.check'));
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
    if (settings.account === 'microsoft' && !msProfile) { toast(t('msg.msneed')); go('profile'); return; }
    launching = true; $('#btn-launch').disabled = true; logLines = []; $('#log').textContent = ''; setProgress('…', 0);
    const r = await api.launch({ version: choice.version, loader: choice.loader, ram: settings.ram, res: settings.res,
      authType: settings.account, nick: profile.nick, fpsBoost: settings.fpsBoost, closeOnLaunch: settings.closeOnLaunch });
    if (!r.ok) { launching = false; $('#btn-launch').disabled = false; setProgress(r.error, 0); addLog(r.error); toast(r.error); }
  });
  $('#btn-log').addEventListener('click', () => api && api.openLog());
  $('#btn-folder').addEventListener('click', () => api && api.openFolder(choice.version));
  $('#mods-folder').addEventListener('click', () => api && api.openFolder(choice.version));

  // OptiFine
  async function refreshOptifine() {
    const need = choice.loader === 'optifine' || choice.loader === 'forgeoptifine';
    $('#optifine-row').classList.toggle('hidden', !need);
    if (!need || !api) return;
    const r = await api.listOptifine(choice.version);
    $('#optifine-have').textContent = r.ok && r.data.length ? t('play.ofhave') + r.data.join(', ') : '';
  }
  $('#btn-optifine-site').addEventListener('click', () => api && api.openOptifineSite());
  $('#btn-optifine-add').addEventListener('click', async () => { if (!api) return; const r = await api.addOptifine(); if (r.ok && r.data) { toast(t('msg.saved')); refreshOptifine(); } });

  // ---------- Моди / ресурс-паки ----------
  let modsTab = 'mods';
  async function renderMods() {
    $$('#mods-tabs .chip').forEach(c => c.classList.toggle('active', c.dataset.tab === modsTab));
    $('#mods-empty-title').textContent = t(modsTab === 'mods' ? 'mods.emptyMods' : 'mods.emptyPacks');
    $('#mods-version').textContent = t('mods.version') + choice.version;
    const list = $('#files-list'); list.innerHTML = '';
    let items = []; if (api) { const r = await api.listFiles(choice.version, modsTab); items = r.ok ? r.data : []; }
    $('#mods-empty').classList.toggle('hidden', items.length > 0);
    items.forEach(it => {
      const li = document.createElement('li'); if (!it.enabled) li.className = 'off';
      const name = document.createElement('span'); name.textContent = it.name; li.appendChild(name);
      const acts = document.createElement('div'); acts.className = 'acts';
      if (modsTab === 'mods') { const b = document.createElement('button'); b.className = 'btn secondary'; b.textContent = t('btn.toggle');
        b.addEventListener('click', async () => { await api.toggleMod(choice.version, it.name); renderMods(); }); acts.appendChild(b); }
      const d = document.createElement('button'); d.className = 'btn secondary'; d.textContent = t('btn.remove');
      d.addEventListener('click', async () => { await api.removeFile(choice.version, modsTab, it.name); renderMods(); }); acts.appendChild(d);
      li.appendChild(acts); list.appendChild(li);
    });
  }
  $$('#mods-tabs .chip').forEach(c => c.addEventListener('click', () => { modsTab = c.dataset.tab; renderMods(); }));
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

  // ---------- Скіни ----------
  function drawSkin(dataUrl) {
    const head = $('#skin-head'), full = $('#skin-full'), side = $('#side-avatar');
    [head, full, side].forEach(c => c.getContext('2d').clearRect(0, 0, c.width, c.height));
    if (!dataUrl) return;
    const img = new Image();
    img.onload = () => {
      [head, side].forEach(c => { const x = c.getContext('2d'); x.imageSmoothingEnabled = false;
        x.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8); x.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8); });
      const f = full.getContext('2d'); f.imageSmoothingEnabled = false; f.drawImage(img, 0, 0);
    };
    img.src = dataUrl;
  }
  $('#skin-file').addEventListener('change', e => {
    const file = e.target.files[0]; if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const img = new Image();
      img.onload = () => {
        if (img.width !== 64 || img.height !== 64) { toast(t('msg.skinBad')); return; }
        (async () => {
          if (api) { const r = await api.setSkin(reader.result); if (!r.ok) { toast(r.error); return; } }
          skin = reader.result; try { localStorage.setItem('nova.skin', skin); } catch {}
          drawSkin(skin); toast(t('msg.skinOk'));
        })();
      };
      img.onerror = () => toast(t('msg.skinBad'));
      img.src = reader.result;
    };
    reader.readAsDataURL(file); e.target.value = '';
  });
  $('#skin-remove').addEventListener('click', () => { skin = null; try { localStorage.removeItem('nova.skin'); } catch {} if (api) api.clearSkin(); drawSkin(null); toast(t('msg.skinGone')); });

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
      const r = await api.modStatus(); if (!r.ok) return;
      $('#mod-status').textContent = r.data.installed ? t('mod.st.ok') : (r.data.bundled ? t('mod.st.no') : t('mod.st.nobundle'));
    };
    paintMod();
    $('#btn-mod').addEventListener('click', async () => {
      const r = await api.installMod();
      if (!r.ok) toast(r.error); else toast(t('mod.st.ok'));
      paintMod();
    });
    $('#btn-update').addEventListener('click', async () => { const r = await api.checkUpdates(true); if (!r.ok) toast(t('upd.err')); });
    if (settings.autoUpdate) api.checkUpdates(false);   // тихо: без інтернету просто нічого не станеться
  }

  // ---------- Старт ----------
  paintIcons(); applyLang(); drawSkin(skin); refreshMs();
})();
