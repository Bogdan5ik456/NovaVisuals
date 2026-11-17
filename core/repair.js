// Автовиправлення: перевіряє типові проблеми лаунчера і сама лагодить те, що можна лагодити безпечно
const fs = require('fs');
const path = require('path');
const { app } = require('electron');
const paths = require('./paths');
const fabric = require('./fabric');
const site = require('./site');
const { online } = require('./util');

let AdmZip = null;
try { AdmZip = require('adm-zip'); } catch {}

const VERSIONS = ['1.18.2', '1.21.11'];

// ok = усе добре, fixed = виправлено, warn = зверни увагу, bad = потрібна дія від тебе
const item = (status, text) => ({ status, text });

function walk(dir, depth, cb) {
  if (depth < 0 || !fs.existsSync(dir)) return;
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const f = path.join(dir, e.name);
    if (e.isDirectory()) walk(f, depth - 1, cb); else cb(f);
  }
}

// Підказки за текстом помилок у журналі останнього запуску
const LOG_HINTS = [
  [/OutOfMemoryError|java heap space/i, 'У журналі є нестача пам’яті. Збільш «Оперативну пам’ять» у Налаштуваннях (наприклад, до 4–6 ГБ).'],
  [/Could not reserve enough space|Invalid maximum heap size/i, 'Java не змогла виділити стільки пам’яті. Зменш «Оперативну пам’ять» у Налаштуваннях.'],
  [/UnsupportedClassVersionError/i, 'Версія Java не підходить до мода чи версії гри. Лаунчер сам завантажує потрібну Java: запусти гру ще раз.'],
  [/Mixin apply failed|InvalidInjectionException|Mixin transformation/i, 'Якийсь мод конфліктує з грою. Увімкни «Діагностика: запуск без мода Nova Visuals» або прибери зайві моди.'],
  [/ZipException|invalid (CEN|LOC) header|zip END header not found/i, 'Один із файлів пошкоджений. Запусти автовиправлення ще раз: пошкоджені моди будуть відкладені.'],
  [/ENOSPC|No space left/i, 'На диску закінчилося місце. Звільни хоча б 3 ГБ.'],
  [/ETIMEDOUT|ENOTFOUND|ECONNRESET|fetch failed/i, 'У журналі є помилки мережі. Перевір інтернет і спробуй ще раз.'],
  [/Authentication|Invalid session|401/i, 'Схоже на помилку входу в акаунт Microsoft. Вийди з акаунта й увійди знову на сторінці «Профіль».']
];

async function run(logFile) {
  const out = [];
  const step = async (name, fn) => {
    try { const r = await fn(); (Array.isArray(r) ? r : [r]).filter(Boolean).forEach(x => out.push(x)); }
    catch (e) { out.push(item('warn', name + ': не вдалося перевірити (' + (e && e.message ? e.message : e) + ')')); }
  };

  await step('Інтернет', async () => (await online()) ? item('ok', 'Інтернет працює') : item('bad', 'Немає інтернету: завантаження гри й вхід через сайт не працюватимуть'));

  await step('Папки', () => {
    let made = 0;
    for (const d of [paths.root, paths.runtime, paths.cache, path.join(paths.root, 'instances'), path.join(paths.root, 'logs')]) {
      if (!fs.existsSync(d)) { fs.mkdirSync(d, { recursive: true }); made++; }
    }
    return made ? item('fixed', 'Відновлено відсутні папки лаунчера (' + made + ')') : item('ok', 'Папки лаунчера на місці');
  });

  await step('Диск', () => {
    const s = fs.statfsSync(paths.root);
    const gb = s.bavail * s.bsize / 1073741824;
    if (gb < 0.5) return item('bad', 'На диску лишилося лише ' + gb.toFixed(1) + ' ГБ: гра не встановиться. Звільни місце.');
    if (gb < 3) return item('warn', 'На диску мало місця (' + gb.toFixed(1) + ' ГБ). Для нової версії гри потрібно 3+ ГБ.');
    return item('ok', 'Місця на диску достатньо (' + Math.floor(gb) + ' ГБ)');
  });

  await step('Недокачані файли', () => {
    let n = 0; const old = Date.now() - 5 * 60e3;
    for (const d of [paths.cache, paths.runtime, path.join(paths.root, 'libraries')]) {
      walk(d, 4, f => { if (f.endsWith('.part')) { try { if (fs.statSync(f).mtimeMs < old) { fs.rmSync(f, { force: true }); n++; } } catch {} } });
    }
    return n ? item('fixed', 'Видалено недокачані файли: ' + n) : item('ok', 'Недокачаних файлів немає');
  });

  for (const v of VERSIONS) {
    await step('Мод ' + v, () => {
      const res = [];
      const mods = paths.modsDir(v);
      if (!fs.existsSync(paths.instance(v))) return item('ok', 'Minecraft ' + v + ': ще не запускався (це нормально)');
      fs.mkdirSync(mods, { recursive: true });
      // пошкоджені моди (порожні або не zip) відкладаємо в mods-broken
      const broken = path.join(paths.instance(v), 'mods-broken');
      let moved = 0;
      for (const f of fs.readdirSync(mods).filter(x => /\.jar$/i.test(x))) {
        const full = path.join(mods, f);
        let bad = false;
        try {
          if (fs.statSync(full).size === 0) bad = true;
          else if (AdmZip) new AdmZip(full).getEntries();
        } catch { bad = true; }
        if (bad) {
          fs.mkdirSync(broken, { recursive: true });
          try { fs.chmodSync(full, 0o666); } catch {}
          fs.renameSync(full, path.join(broken, f));
          moved++;
        }
      }
      if (moved) res.push(item('fixed', 'Minecraft ' + v + ': пошкоджені моди відкладено в папку mods-broken (' + moved + ')'));
      const st = fabric.novaStatus(v);
      if (st.bundled && !st.installed) { fabric.installNovaMod(v); res.push(item('fixed', 'Minecraft ' + v + ': мод Nova Visuals встановлено заново')); }
      else if (!moved) res.push(item('ok', 'Minecraft ' + v + ': моди в порядку'));
      return res;
    });
  }

  await step('Сайт', () => {
    const res = [];
    const ud = app.getPath('userData');
    let cfg = {};
    try { cfg = JSON.parse(fs.readFileSync(path.join(__dirname, 'supabase.json'), 'utf8')); } catch {}
    if (!/^https:\/\/[^\s/]+\.supabase\.co$/i.test(String(cfg.url || '').trim()) || String(cfg.key || '').trim().length < 20)
      res.push(item('bad', 'У core/supabase.json не вписані url і key від Supabase: вхід через сайт не працюватиме.'));
    else res.push(item('ok', 'Ключі сайту вписані'));
    const urlFile = path.join(ud, 'site-config.json');
    if (fs.existsSync(urlFile)) {
      let u = '';
      try { u = JSON.parse(fs.readFileSync(urlFile, 'utf8')).url || ''; } catch {}
      if (!/^https?:\/\/[^\s/]+(:\d+)?$/i.test(u)) { fs.rmSync(urlFile, { force: true }); res.push(item('fixed', 'Адресу сайту в Налаштуваннях було зіпсовано: скинуто до стандартної')); }
    }
    const tok = path.join(ud, 'site-token.bin'), info = path.join(ud, 'site-profile.json');
    if ((fs.existsSync(tok) || fs.existsSync(info)) && !site.status()) {
      fs.rmSync(tok, { force: true }); fs.rmSync(info, { force: true });
      res.push(item('fixed', 'Збережений вхід через сайт був пошкоджений: скинуто. Увійди через сайт ще раз (Профіль).'));
    }
    return res;
  });

  await step('Журнал', () => {
    if (!logFile || !fs.existsSync(logFile)) return item('ok', 'Журнал помилок порожній');
    const size = fs.statSync(logFile).size, len = Math.min(size, 200 * 1024);
    const fd = fs.openSync(logFile, 'r'), buf = Buffer.alloc(len);
    fs.readSync(fd, buf, 0, len, size - len); fs.closeSync(fd);
    const txt = buf.toString('utf8');
    const hints = LOG_HINTS.filter(([re]) => re.test(txt)).map(([, h]) => item('warn', h));
    return hints.length ? hints : item('ok', 'У журналі останнього запуску типових помилок не знайдено');
  });

  const fixed = out.filter(x => x.status === 'fixed').length, bad = out.filter(x => x.status === 'bad').length;
  return { items: out, summary: bad ? 'Потрібні твої дії: ' + bad : (fixed ? 'Виправлено: ' + fixed : 'Проблем не знайдено') };
}

module.exports = { run };
